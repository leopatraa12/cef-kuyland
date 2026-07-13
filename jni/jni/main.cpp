#include <jni.h>
#include <pthread.h>
#include <cmath>
#include <cstring>
#include <sstream>
#include <string>
//#include <syscall.h>

#include "main.h"
#include "game/game.h"
#include "net/netgame.h"
#include "gui/gui.h"
#include "gui/uisettings.h"
#include "playertags.h"
#include "audiostream.h"
#include "java/jniutil.h"
#include <dlfcn.h>
#include "StackTrace.h"
#include "vendor/cef/SAMPMobileCef.h"
// voice
#include "voice_new/Plugin.h"

#include "vendor/armhook/armhook.h"
#include "vendor/str_obfuscator/str_obfuscator.hpp"

#include "settings.h"

#include "crashlytics.h"

/*
Peerapol Unarak
*/

JavaVM* javaVM;

UI* pUI = nullptr;
CGame *pGame = nullptr;

CNetGame *pNetGame = nullptr;
CPlayerTags* pPlayerTags = nullptr;
CSnapShotHelper* pSnapShotHelper = nullptr;
CAudioStream* pAudioStream = nullptr;
CJavaWrapper* pJavaWrapper = nullptr;
CSettings* pSettings = nullptr;
//CVoice* pVoice = nullptr;

bool g_bHudVisible = false;
bool g_bDialogVisible = false;

MaterialTextGenerator* pMaterialTextGenerator = nullptr;

bool bDebug = false;
bool bGameInited = false;
bool bNetworkInited = false;
static constexpr bool kUseNativeCrashlytics = false;

uintptr_t g_libGTASA = 0x00;
uintptr_t g_libSAMP = 0x00;

void ApplyGlobalPatches();
void ApplyMultiTouchPatches();
void InstallGlobalHooks();
void InitializeRenderWare();
void FLog(const char* fmt, ...);
//void MyLog(const char* fmt, ...);

int work = 0;

bool Mchat = false;

static bool IsWeaponIdValidForWheel(uint32_t weaponId)
{
	return weaponId <= 46;
}

static std::string BuildWeaponWheelPayload(CPlayerPed* pPlayerPed)
{
	if (!pPlayerPed || !pPlayerPed->m_pPed)
	{
		return "[{\"id\":0,\"ammo\":0,\"current\":true}]";
	}

	uint8_t currentWeapon = pPlayerPed->GetCurrentWeapon();
	bool seen[47] = {};
	std::ostringstream stream;
	stream << "[";

	auto appendWeapon = [&](uint32_t weaponId, uint32_t ammo, bool current) {
		if (!IsWeaponIdValidForWheel(weaponId) || seen[weaponId])
		{
			return;
		}
		if (seen[0] || weaponId != 0)
		{
			stream << ",";
		}
		stream << "{\"id\":" << weaponId
			   << ",\"ammo\":" << ammo
			   << ",\"current\":" << (current ? "true" : "false")
			   << "}";
		seen[weaponId] = true;
	};

	appendWeapon(0, 0, currentWeapon == 0);

	for (int i = 0; i < 13; ++i)
	{
		WEAPON_SLOT_TYPE& slot = pPlayerPed->m_pPed->WeaponSlots[i];
		uint32_t weaponId = slot.dwType;
		if (weaponId == 0 || !IsWeaponIdValidForWheel(weaponId))
		{
			continue;
		}
		appendWeapon(weaponId, slot.dwAmmo, currentWeapon == weaponId);
	}

	stream << "]";
	return stream.str();
}

static void UpdateWeaponWheelSnapshot(CPlayerPed* pPlayerPed)
{
	if (!pJavaWrapper)
	{
		return;
	}

	static std::string s_lastWeaponWheelPayload;
	std::string payload = BuildWeaponWheelPayload(pPlayerPed);
	if (payload == s_lastWeaponWheelPayload)
	{
		return;
	}

	s_lastWeaponWheelPayload = payload;
	pJavaWrapper->UpdateWeaponWheel(s_lastWeaponWheelPayload.c_str());
}

struct SampChatCfgPtrs
{
	float* posX;
	float* posY;
	float* sizeX;
	float* sizeY;
	int* maxMsgs;
};

struct SampChatScaleInfo
{
	float scaleX;
	float scaleY;
	bool originBottom;
};

static SampChatCfgPtrs g_sampChatCfg = { nullptr, nullptr, nullptr, nullptr, nullptr };
static bool g_sampChatCfgReady = false;
static bool g_sampChatCfgTried = false;
static SampChatScaleInfo g_sampChatScale = { 1.0f, 1.0f, false };

static bool FloatEqRel(float a, float b, float absEps = 1.0f, float relEps = 0.02f)
{
	const float diff = fabsf(a - b);
	const float maxAbs = fmaxf(fabsf(a), fabsf(b));
	return diff <= fmaxf(absEps, relEps * maxAbs);
}

static bool IsPlausiblePos(float x, float y, float dispW, float dispH)
{
	if (!std::isfinite(x) || !std::isfinite(y)) {
		return false;
	}
	return x >= 0.0f && x <= dispW && y >= 0.0f && y <= dispH;
}

static bool ScanRangeForChatBySize(uintptr_t start, uintptr_t end, float sizeX, float sizeY,
	float dispW, float dispH, SampChatCfgPtrs* out)
{
	for (uintptr_t addr = start; addr + sizeof(float) * 2 <= end; addr += 4)
	{
		float* f = reinterpret_cast<float*>(addr);
		if (!FloatEqRel(f[0], sizeX, 6.0f, 0.08f) || !FloatEqRel(f[1], sizeY, 6.0f, 0.08f)) {
			continue;
		}

		for (int off = -32; off <= 32; off += 4)
		{
			uintptr_t paddr = addr + off;
			if (paddr < start || paddr + sizeof(float) * 2 > end) {
				continue;
			}

			float* p = reinterpret_cast<float*>(paddr);
			float px = p[0];
			float py = p[1];
			if (!IsPlausiblePos(px, py, dispW, dispH)) {
				continue;
			}

			out->posX = &p[0];
			out->posY = &p[1];
			out->sizeX = &f[0];
			out->sizeY = &f[1];
			out->maxMsgs = nullptr;
			return true;
		}
	}

	return false;
}

static bool FindSampChatConfig()
{
	const float dispW = RsGlobal ? RsGlobal->maximumWidth : 1920.0f;
	const float dispH = RsGlobal ? RsGlobal->maximumHeight : 1080.0f;
	const float baseW = 640.0f;
	const float baseH = 480.0f;
	const float sx = dispW / baseW;
	const float sy = dispH / baseH;

	const float sizeXBase = pSettings ? pSettings->Get().fChatSizeX : 1150.0f;
	const float sizeYBase = pSettings ? pSettings->Get().fChatSizeY : 220.0f;
	const float sizeXScaled = sizeXBase * sx;
	const float sizeYScaled = sizeYBase * sy;

	const float sizeCandidates[4][2] = {
		{ sizeXBase, sizeYBase },
		{ sizeXScaled, sizeYScaled },
		{ sizeYBase, sizeXBase },
		{ sizeYScaled, sizeXScaled }
	};

	FILE* fp = fopen("/proc/self/maps", "rt");
	if (!fp) {
		return false;
	}

	char line[512];
	while (fgets(line, sizeof(line), fp))
	{
		uintptr_t start = 0;
		uintptr_t end = 0;
		char perms[5] = { 0 };

		if (sscanf(line, "%lx-%lx %4s", &start, &end, perms) != 3) {
			continue;
		}

		if (!(perms[0] == 'r' && perms[1] == 'w')) {
			continue;
		}

		if (!strstr(line, "libSAMP.so") &&
			!strstr(line, "libGTASA.so") &&
			!strstr(line, "[anon]") &&
			!strstr(line, "[heap]"))
		{
			continue;
		}

		if (end <= start || (end - start) > (64 * 1024 * 1024)) {
			continue;
		}

		for (int i = 0; i < 4; ++i)
		{
			if (ScanRangeForChatBySize(start, end, sizeCandidates[i][0], sizeCandidates[i][1],
				dispW, dispH, &g_sampChatCfg))
			{
				const float curSizeX = *g_sampChatCfg.sizeX;
				const float curSizeY = *g_sampChatCfg.sizeY;

				g_sampChatScale.scaleX = (fabsf(curSizeX - sizeXScaled) < fabsf(curSizeX - sizeXBase)) ? sx : 1.0f;
				g_sampChatScale.scaleY = (fabsf(curSizeY - sizeYScaled) < fabsf(curSizeY - sizeYBase)) ? sy : 1.0f;

				const float refH = (g_sampChatScale.scaleY > 1.1f) ? dispH : baseH;
				const float margin = 10.0f * ((g_sampChatScale.scaleY > 1.1f) ? g_sampChatScale.scaleY : 1.0f);
				const float topY = margin;
				const float bottomY = refH - curSizeY - margin;
				const float curY = *g_sampChatCfg.posY;

				g_sampChatScale.originBottom = fabsf(curY - bottomY) < fabsf(curY - topY);

				fclose(fp);
				FLog("SAMP chat config found at %p (scaleX=%.3f scaleY=%.3f bottom=%d)",
					g_sampChatCfg.posX, g_sampChatScale.scaleX, g_sampChatScale.scaleY, g_sampChatScale.originBottom ? 1 : 0);
				return true;
			}
		}
	}

	fclose(fp);
	return false;
}

static bool IsRadarVisible()
{
	if (!g_bHudVisible) {
		return false;
	}
	if (g_bDialogVisible) {
		return false;
	}
	if (pNetGame) {
		CTextDrawPool* pTextDrawPool = pNetGame->GetTextDrawPool();
		if (pTextDrawPool && pTextDrawPool->GetState()) {
			return false;
		}
	}
	return true;
}

static void UpdateSampChatPos(bool radarVisible)
{
	if (!g_sampChatCfgReady) {
		return;
	}

	const float baseW = 640.0f;
	const float baseH = 480.0f;
	const float dispW = RsGlobal ? RsGlobal->maximumWidth : baseW * g_sampChatScale.scaleX;
	const float dispH = RsGlobal ? RsGlobal->maximumHeight : baseH * g_sampChatScale.scaleY;
	const bool useDisplayCoords = (g_sampChatScale.scaleX < 1.2f && g_sampChatScale.scaleY < 1.2f);
	const float refW = useDisplayCoords ? dispW : baseW;
	const float refH = useDisplayCoords ? dispH : baseH;
	const float scaleX = useDisplayCoords ? 1.0f : g_sampChatScale.scaleX;
	const float scaleY = useDisplayCoords ? 1.0f : g_sampChatScale.scaleY;
	const float sizeY = g_sampChatCfg.sizeY ? *g_sampChatCfg.sizeY
											: (pSettings ? pSettings->Get().fChatSizeY : 220.0f);
	const float radarSize = refW * 0.19f;
	const float marginY = 10.0f;
	const float marginXNoRadar = 10.0f;
	const float radarOffset = -refW * 0.03f;

	float posXBase = radarVisible ? (radarSize + radarOffset) : marginXNoRadar;
	float posYBase = marginY;
	if (g_sampChatScale.originBottom) {
		posYBase = refH - sizeY - marginY;
	}

	const float posX = posXBase * scaleX;
	const float posY = posYBase * scaleY;

	ARMHook::unprotect(reinterpret_cast<uintptr_t>(g_sampChatCfg.posX), sizeof(float) * 2);
	*g_sampChatCfg.posX = posX;
	*g_sampChatCfg.posY = posY;
}

static void NativeCrashlyticsInitialize()
{
	if (!kUseNativeCrashlytics) {
		return;
	}

	firebase::crashlytics::Initialize();
}

static void NativeCrashlyticsSetCustomKey(const char* key, const char* value)
{
	if (!kUseNativeCrashlytics || !key || !value) {
		return;
	}

	firebase::crashlytics::SetCustomKey(key, value);
}

static void NativeCrashlyticsSetUserId(const char* userId)
{
	if (!kUseNativeCrashlytics || !userId || !userId[0]) {
		return;
	}

	firebase::crashlytics::SetUserId(userId);
}

static void NativeCrashlyticsLog(const char* message)
{
	if (!kUseNativeCrashlytics || !message || !message[0]) {
		return;
	}

	firebase::crashlytics::Log(message);
}

void ReadSettingFile()
{
	/*char path[255] = { 0 };
	//sprintf(path, "%ssamp.set", pGame->GetDataDirectory());
	sprintf(path, "%sNickName.ini", pGame->GetDataDirectory());

	FILE* fp = fopen(path, "r");
	if (fp == NULL) return;

	char buf[1024];

	// nickname
	if (fgets(buf, 1024, fp) != NULL) {
		buf[strcspn(buf, "\n\r")] = 0;
		strcpy(g_nick, buf);
	}

	fclose(fp);*/

	if (pSettings) {
		return;
	}

	pSettings = new CSettings();
	NativeCrashlyticsSetUserId(pSettings->Get().szNickName);
}

int hashing(const char* str) {
	int hashing = 5381;
	int c;
	while (c = *str++) {
		hashing = ((hashing << 5) + hashing) + c; /* hash * 33 + c */
		if (hashing < 0) hashing = 100;
	}
	if (hashing < 0) hashing = 100;
	return hashing;
}


int _curlWriteFunc(char* data, size_t size, size_t nmemb, std::string* buffer)
{
	int result = 0;
	if (buffer != nullptr)
	{
		buffer->append(data, size * nmemb);
		result = size * nmemb;
	}
	return result;
}

void SkipRockStarLegal()
{
	uintptr_t adr = ARMHook::getLibraryAddress("libSCAnd.so");
	if (adr == 0) return;

	ARMHook::unprotect(adr + /*0x20C670*/0x31C149);
	*(bool*)(adr + /*0x20C670*/0x31C149) = true;
}

void* Init(void*)
{
	SkipRockStarLegal(); //Skip LegalScreenShown

	while (true)
	{
		if (*(int*)(g_libGTASA + 0xA987C8) == 7) {
			pGame->StartGame();
			break;
		}
		else {
			usleep(500);
		}
	}

	pthread_exit(0);
}

void DoDebugLoop()
{
	// ...
}

void DoDebugStuff()
{
	// ...

	MATRIX4X4 mat;
	pGame->FindPlayerPed()->GetMatrix(&mat);
	
	for (int i = 0; i < 100; i++)
	{
		CPlayerPed* ped = pGame->NewPlayer(i, mat.pos.X + i, mat.pos.Y, mat.pos.Z, 0.0f, false, false);
		//ped->SetCollisionChecking(false);
		//ped->SetGravityProcessing(false);
	}
}

void printAddressBacktrace(const unsigned address, void* pc, void* lr)
{
	char filename[0xFF];
	sprintf(filename, "/proc/%d/maps", getpid());
	FILE* m_fp = fopen(filename, "rt");
	if (m_fp == nullptr)
	{
		FLog("ERROR: can't open file %s", filename);
		return;
	}
		Dl_info info_pc, info_lr;
		memset(&info_pc, 0, sizeof(Dl_info));
		memset(&info_lr, 0, sizeof(Dl_info));
		dladdr(pc, &info_pc);
		dladdr(lr, &info_lr);

		rewind(m_fp);
		char buffer[2048] = { 0 };
		while (fgets(buffer, sizeof(buffer), m_fp))
		{
			const auto start_address = strtoul(buffer, nullptr, 16);
			const auto end_address = strtoul(strchr(buffer, '-') + 1, nullptr, 16);

			if (start_address <= address && end_address > address)
			{
				if (*(strchr(buffer, ' ') + 3) == 'x')
					FLog("Call: %X (GTA: %X PC: %s LR: %s) (SAMP: %X) (libc: %X)", address, address - g_libGTASA, info_pc.dli_sname, info_lr.dli_sname, address - ARMHook::getLibraryAddress("libSAMP.so"), address - ARMHook::getLibraryAddress("libc.so"));
				break;
			}
		}
}

struct sigaction act_old;
struct sigaction act1_old;
struct sigaction act2_old;
struct sigaction act3_old;

extern int g_iLastProcessedSkinCollision, g_iLastProcessedEntityCollision, g_iLastRenderedObject;
extern uintptr_t g_dwLastRetAddrCrash;
void handler(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act_old.sa_sigaction)
	{
		act_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGSEGV)
	{
		FLog("SIGSEGV | Fault address: 0x%X", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler1(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act1_old.sa_sigaction)
	{
		act1_old.sa_sigaction(signum, info, contextPtr);
	}

if(info->si_signo == SIGABRT)
	{
		FLog("SIGABRT | Fault address: 0x%X", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler2(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act2_old.sa_sigaction)
	{
		act2_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGFPE)
	{
		FLog("SIGFPE | Fault address: 0x%X", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler3(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act3_old.sa_sigaction)
	{
		act3_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGBUS)
	{
		FLog("SIGBUS | Fault address: 0x%X", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void DoInitStuff()
{
	if (bGameInited == false)
	{
		pPlayerTags = new CPlayerTags();
		pSnapShotHelper = new CSnapShotHelper();
		pMaterialTextGenerator = new MaterialTextGenerator();
		pAudioStream = new CAudioStream();
		pAudioStream->Initialize();

		//pUI->splashscreen()->setVisible(false);
		if (pJavaWrapper) {
			pJavaWrapper->HideLoadingScreen();
		}
		pUI->chat()->setVisible(true);
		Mchat = true;

		pGame->Initialize();
		pGame->SetMaxStats();
		pGame->ToggleThePassingOfTime(false);

		if (!g_sampChatCfgTried) {
			g_sampChatCfgReady = FindSampChatConfig();
			g_sampChatCfgTried = true;
			UpdateSampChatPos(IsRadarVisible());
		}

		// voice
		LogVoice("[dbg:samp:load] : module loading...");

		for (const auto& loadCallback : Samp::loadCallbacks) {
			if (loadCallback != nullptr) {
				loadCallback();
			}
		}

		Samp::loadStatus = true;

		LogVoice("[dbg:samp:load] : module loaded");

		if (bDebug)
		{
			pGame->GetCamera()->Restore();
			pGame->GetCamera()->SetBehindPlayer();
			pGame->DisplayHUD(true);
			pGame->EnableClock(false);

			DoDebugStuff();
		}

		bGameInited = true;
	}

	if (!bNetworkInited && !bDebug)
	{
		if (!pSettings) {
			FLog("Settings were not initialized before network startup; loading now.");
			ReadSettingFile();
		}

		if (!pSettings) {
			FLog("Settings are unavailable; network startup skipped.");
			return;
		}

		const char* host = pSettings->Get().szHost;
		int port = pSettings->Get().iPort;

		if (host == nullptr || host[0] == '\0') {
			host = cryptor::create("15.228.76.174").decrypt();
		}

		if (port <= 0) {
			port = 7777;
		}

		pNetGame = new CNetGame(host, port, pSettings->Get().szNickName, pSettings->Get().szPassword);
		bNetworkInited = true;
	}
}

extern "C" {
	static int g_nativeOverlayState = 0;
	static bool g_allowNextNativePauseMenu = false;

	static void InitializeSAMPBridge(JNIEnv *pEnv, jobject thiz)
	{
		ReadSettingFile();
		if (!pJavaWrapper) {
			pJavaWrapper = new CJavaWrapper(pEnv, thiz);
		}
	}

	static void OnInputEndBridge(JNIEnv *pEnv, jobject thiz, jbyteArray str)
	{
		if(pUI)
		{
			pUI->keyboard()->sendForGB(pEnv, thiz, str);
		}
	}

	static void OnEventBackPressedBridge()
	{
		if(pSettings && pJavaWrapper && pSettings->Get().iAndroidKeyboard) {
			pJavaWrapper->HideKeyboard();
		}
	}

	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_initializeSAMP(JNIEnv *pEnv, jobject thiz)
	{
		InitializeSAMPBridge(pEnv, thiz);
	}
	JNIEXPORT void JNICALL Java_com_raiferoleplay_game_game_SAMP_initializeSAMP(JNIEnv *pEnv, jobject thiz)
	{
		InitializeSAMPBridge(pEnv, thiz);
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_onInputEnd(JNIEnv *pEnv, jobject thiz, jbyteArray str)
	{
		OnInputEndBridge(pEnv, thiz, str);
	}
	JNIEXPORT void JNICALL Java_com_raiferoleplay_game_game_SAMP_onInputEnd(JNIEnv *pEnv, jobject thiz, jbyteArray str)
	{
		OnInputEndBridge(pEnv, thiz, str);
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_onEventBackPressed(JNIEnv *pEnv, jobject thiz)
	{
		OnEventBackPressedBridge();
	}
	JNIEXPORT void JNICALL Java_com_raiferoleplay_game_game_SAMP_onEventBackPressed(JNIEnv *pEnv, jobject thiz)
	{
		OnEventBackPressedBridge();
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_setNativeOverlayState(JNIEnv *pEnv, jobject thiz, jint overlayType)
	{
		g_nativeOverlayState = overlayType;
	}
	JNIEXPORT jint JNICALL Java_com_xyron_game_main_SAMP_getNativeOverlayState(JNIEnv *pEnv, jobject thiz)
	{
		return g_nativeOverlayState;
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_setAllowNextNativePauseMenu(JNIEnv *pEnv, jobject thiz, jboolean allow)
	{
		g_allowNextNativePauseMenu = (allow == JNI_TRUE);
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_forceEndNativeUserPause(JNIEnv *pEnv, jobject thiz)
	{
		g_allowNextNativePauseMenu = false;
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_SAMP_sendSyntheticNativeTouch(JNIEnv *pEnv, jobject thiz, jint x, jint y)
	{
		(void)x;
		(void)y;
	}
	JNIEXPORT jfloatArray JNICALL Java_com_xyron_game_main_SAMP_getPlayerPlacementSnapshot(JNIEnv *pEnv, jobject thiz)
	{
		return nullptr;
	}
	JNIEXPORT jboolean JNICALL Java_com_xyron_game_main_SAMP_showLocalPickupPreview(JNIEnv *pEnv, jobject thiz, jint modelId, jint pickupType, jfloat x, jfloat y, jfloat z)
	{
		(void)modelId;
		(void)pickupType;
		(void)x;
		(void)y;
		(void)z;
		return JNI_FALSE;
	}
	JNIEXPORT void JNICALL Java_com_nvidia_devtech_NvEventQueueActivity_nativeImGuiRenderFrame(JNIEnv *pEnv, jobject thiz)
	{
	}
	JNIEXPORT void JNICALL Java_com_nvidia_devtech_NvEventQueueActivity_nativeImGuiTouchEvent(JNIEnv *pEnv, jobject thiz, jint action, jint pointer, jint x, jint y)
	{
		(void)action;
		(void)pointer;
		(void)x;
		(void)y;
	}
	JNIEXPORT void JNICALL Java_com_xyron_game_main_ui_dialog_DialogManager_sendDialogResponse(JNIEnv* pEnv, jobject thiz, jint i3, jint i, jint i2, jbyteArray str)
	{
		jboolean isCopy = true;

		jbyte* pMsg = pEnv->GetByteArrayElements(str, &isCopy);
		jsize length = pEnv->GetArrayLength(str);

		std::string szStr((char*)pMsg, length);

		if(pNetGame) {
			pNetGame->SendDialogResponse(i, i3, i2, (char*)szStr.c_str());
			//pGame->FindPlayerPed()->TogglePlayerControllableWithoutLock(true);
		}

		pEnv->ReleaseByteArrayElements(str, pMsg, JNI_ABORT);
	}

	JNIEXPORT void JNICALL Java_com_xyron_game_main_ui_dialog_DialogManager_setDialogVisible(JNIEnv* pEnv, jobject thiz, jboolean visible)
	{
		g_bDialogVisible = (visible == JNI_TRUE);
		if (!pGame) {
			return;
		}

		if (g_bDialogVisible) {
			pGame->DisplayHUD(false);
			return;
		}

		if (IsRadarVisible()) {
			pGame->DisplayHUD(true);
		}
	}
}

void MainLoop()
{
	if (pGame->bIsGameExiting) return;

	DoInitStuff();

	if (g_bDialogVisible && pGame) {
		// Forca o radar a ficar oculto enquanto dialog custom estiver visivel
		pGame->DisplayHUD(false);
	}

	// Tenta localizar config do chat original do SA-MP (caso ainda nÃ£o tenha encontrado)
	static uint32_t s_lastChatScan = 0;
	if (!g_sampChatCfgReady)
	{
		uint32_t now = GetTickCount();
		if (now - s_lastChatScan > 2000)
		{
			g_sampChatCfgReady = FindSampChatConfig();
			s_lastChatScan = now;
			if (g_sampChatCfgReady)
			{
				UpdateSampChatPos(IsRadarVisible());
			}
		}
	}

	// Ajusta a posiÃ§Ã£o do chat conforme o HUD Java (quando HUD aparece, radar some)
	static int s_lastRadarVisible = -1;
	if (pUI) {
		int radarVisible = IsRadarVisible() ? 1 : 0;
		if (radarVisible != s_lastRadarVisible) {
			pUI->chat()->setPosition(radarVisible ? UISettings::chatPos()
												  : UISettings::chatPosNoRadar());
			s_lastRadarVisible = radarVisible;
		}
	}

	if (bDebug) {
		DoDebugLoop();
	}

	if (pNetGame) {
		pNetGame->Process();

		CTextDrawPool* pTextDrawPool = pNetGame->GetTextDrawPool();
		if(pTextDrawPool) pTextDrawPool->Draw();
	}

	if (pNetGame)
	{
		if (pNetGame->GetPlayerPool())
		{
			if (pNetGame->GetPlayerPool()->GetLocalPlayer())
			{
				CPlayerPed* pLocalPlayerPed = pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed();
				if (pLocalPlayerPed && pNetGame->GetGameState() == GAMESTATE_CONNECTED)
				{
					pGame->DisplayHUD(false);
					*(uint8_t*)(g_libGTASA + /*0x8EF36B*/0x991FD8) = g_bDialogVisible ? 1 : 0;
					pJavaWrapper->UpdateHud(pLocalPlayerPed->GetHealth(),
											pLocalPlayerPed->GetArmour(),
											pNetGame->eatProcent,
											pGame->GetLocalMoney(),
											pLocalPlayerPed->GetCurrentWeapon(),	pLocalPlayerPed->GetCurrentWeaponSlot()->dwAmmo);
					UpdateWeaponWheelSnapshot(pLocalPlayerPed);

				}
				else {
					*(uint8_t*)(g_libGTASA + /*0x8EF36B*/0x991FD8) = 1;
					pJavaWrapper->HideHud();
				}

			}
		}
	}

	// ForÃ§a a posiÃƒÂ§ÃƒÂ£o do chat original do SA-MP sempre no fim do frame
	if (g_sampChatCfgReady)
	{
		UpdateSampChatPos(IsRadarVisible());
	}

	if (pAudioStream) {
		pAudioStream->Process();
	}

}

void InitGui()
{
	// new voice
	Plugin::OnPluginLoad();
	Plugin::OnSampLoad();

	std::string font_path = string_format("%sfonts/%s", pGame->GetDataDirectory(), FONT_NAME);
	pUI = new UI(ImVec2(RsGlobal->maximumWidth, RsGlobal->maximumHeight), font_path.c_str());
	pUI->initialize();
	pUI->performLayout();
}

#include "game/multitouch.h"
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	javaVM = vm;
	LOGI("SA-MP library loaded! Build time: " __DATE__ " " __TIME__);

	g_libGTASA = ARMHook::getLibraryAddress("libGTASA.so");
	if (g_libGTASA == 0x00) {
		LOGE("libGTASA.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	g_libSAMP = ARMHook::getLibraryAddress("libSAMP.so");
	if (g_libSAMP == 0x00) {
		LOGE("libSAMP.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	NativeCrashlyticsInitialize();

	uintptr_t libgtasa = ARMHook::getLibraryAddress("libGTASA.so");
	uintptr_t libsamp = ARMHook::getLibraryAddress("libSAMP.so");
	uintptr_t libc = ARMHook::getLibraryAddress("libc.so");

	FLog("libGTASA.so: 0x%x", libgtasa);
	FLog("libSAMP.so: 0x%x", libsamp);
	FLog("libc.so: 0x%x", libc);

	char str[100];

	sprintf(str, "0x%x", libgtasa);
	NativeCrashlyticsSetCustomKey("libGTASA.so", str);
	
	sprintf(str, "0x%x", libsamp);
	NativeCrashlyticsSetCustomKey("libSAMP.so", str);

	sprintf(str, "0x%x", libc);
	NativeCrashlyticsSetCustomKey("libc.so", str);

	LOGI("Loading bass library..");
	LoadBassLibrary();

	ARMHook::initializeTrampolines(g_libGTASA +/*0x180044*/0x1A9E0C, 1024);

	InstallGlobalHooks();
	ApplyGlobalPatches();
	InitializeRenderWare();
	MultiTouch::initialize();

	pGame = new CGame();

	//pVoice = new CVoice();
	//pVoice->Initialize(VOICE_FREQUENCY, CODEC_FREQUENCY, VOICE_SENDRRATE);

	pthread_t thread;
	pthread_create(&thread, 0, Init, 0);

	struct sigaction act;
	act.sa_sigaction = handler;
	sigemptyset(&act.sa_mask);
	act.sa_flags = SA_SIGINFO;
	sigaction(SIGSEGV, &act, &act_old);

	struct sigaction act1;
	act1.sa_sigaction = handler1;
	sigemptyset(&act1.sa_mask);
	act1.sa_flags = SA_SIGINFO;
	sigaction(SIGABRT, &act1, &act1_old);

	struct sigaction act2;
	act2.sa_sigaction = handler2;
	sigemptyset(&act2.sa_mask);
	act2.sa_flags = SA_SIGINFO;
	sigaction(SIGFPE, &act2, &act2_old);

	struct sigaction act3;
	act3.sa_sigaction = handler3;
	sigemptyset(&act3.sa_mask);
	act3.sa_flags = SA_SIGINFO;
	sigaction(SIGBUS, &act3, &act3_old);
		
	return JNI_VERSION_1_6;
}

// never called on Android :(
void JNI_OnUnload(JavaVM *vm, void *reserved)
{
	FLog("SA-MP library unloaded!");

	ARMHook::uninitializeTrampolines();
}

uint32_t GetTickCount()
{
	struct timeval tv;
	gettimeofday(&tv, nullptr);
	return (tv.tv_sec * 1000 + tv.tv_usec / 1000);
}	

void FLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = CGame::GetDataDirectory();
cef::setGamePath(pszStorage);

	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		LOGI("buffer: %s", buffer);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	LOGI("%s", buffer);
	NativeCrashlyticsLog(buffer);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void ChatLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = CGame::GetDataDirectory();


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/chat_log.txt", pszStorage);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void MyLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = CGame::GetDataDirectory();


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		LOGI("buffer: %s", buffer);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void MyLog2(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = CGame::GetDataDirectory();


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		LOGI("buffer: %s", buffer);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (pUI) pUI->chat()->addDebugMessage(buffer);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);
	return;
}

void LogVoice(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = CGame::GetDataDirectory();

	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%sSAMP/%s", pszStorage, SV::kLogFileName);
		flLog = fopen(buffer, "w");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	__android_log_write(ANDROID_LOG_INFO, "AXL", buffer);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

int (*BASS_Init) (uint32_t, uint32_t, uint32_t);
int (*BASS_Free) (void);
char *(*BASS_GetConfigPtr) (uint32_t);
int (*BASS_SetConfigPtr) (uint32_t, const char *);
int (*BASS_GetConfig) (uint32_t);
int (*BASS_SetConfig) (uint32_t, uint32_t);
int (*BASS_ChannelStop) (uint32_t);
int (*BASS_StreamCreateURL) (char *, uint32_t, uint32_t, uint32_t);
int (*BASS_StreamCreate) (uint32_t, uint32_t, uint32_t, STREAMPROC *, void *);
int (*BASS_ChannelPlay) (uint32_t, bool);
int (*BASS_ChannelPause) (uint32_t);
int (*BASS_StreamFree) (uint32_t);
int (*BASS_ErrorGetCode) (void);
int (*BASS_Set3DFactors) (float, float, float);
int (*BASS_Set3DPosition) (const BASS_3DVECTOR *, const BASS_3DVECTOR *, const BASS_3DVECTOR *, const BASS_3DVECTOR *);
int (*BASS_Apply3D) (void);
int (*BASS_ChannelSetFX) (uint32_t, HFX);
int (*BASS_ChannelRemoveFX) (uint32_t, HFX);
int (*BASS_FXSetParameters) (HFX, const void *);
int (*BASS_IsStarted) (void);
int (*BASS_RecordGetDeviceInfo) (uint32_t, BASS_DEVICEINFO *);
int (*BASS_RecordInit) (int);
int (*BASS_RecordGetDevice) (void);
int (*BASS_RecordFree) (void);
int (*BASS_RecordStart) (uint32_t, uint32_t, uint32_t, RECORDPROC *, void *);
int (*BASS_ChannelSetAttribute) (uint32_t, uint32_t, float);
int (*BASS_ChannelGetData) (uint32_t, void *, uint32_t);
int (*BASS_RecordSetInput) (int, uint32_t, float);
int (*BASS_StreamPutData) (uint32_t, const void *, uint32_t);
int (*BASS_ChannelSetPosition) (uint32_t, uint64_t, uint32_t);
int (*BASS_ChannelIsActive) (uint32_t);
int (*BASS_ChannelSlideAttribute) (uint32_t, uint32_t, float, uint32_t);
int (*BASS_ChannelSet3DAttributes) (uint32_t, int, float, float, int, int, float);
int (*BASS_ChannelSet3DPosition) (uint32_t, const BASS_3DVECTOR *, const BASS_3DVECTOR *, const BASS_3DVECTOR *);
int (*BASS_SetVolume) (float);
const char *(*BASS_ChannelGetTags) (uint32_t handle, uint32_t tags);
int (*BASS_ChannelSetSync) (uint32_t handle, uint32_t type, uint64_t param, SYNCPROC *proc, void *user);

void LoadBassLibrary() {
	void *v0 = dlopen("/data/data/com.xyron.game/lib/libbass.so", 1);
	if (!v0) FLog("%s", dlerror());

	BASS_Init = (int (*)(uint32_t, uint32_t, uint32_t)) dlsym(v0, "BASS_Init");
	BASS_Free = (int (*)(void)) dlsym(v0, "BASS_Free");
	BASS_GetConfigPtr = (char *(*)(uint32_t)) dlsym(v0, "BASS_GetConfigPtr");
	BASS_SetConfigPtr = (int (*)(uint32_t, const char *)) dlsym(v0, "BASS_SetConfigPtr");
	BASS_GetConfig = (int (*)(uint32_t)) dlsym(v0, "BASS_GetConfig");
	BASS_SetConfig = (int (*)(uint32_t, uint32_t)) dlsym(v0, "BASS_SetConfig");
	BASS_ChannelStop = (int (*)(uint32_t)) dlsym(v0, "BASS_ChannelStop");
	BASS_StreamCreateURL = (int (*)(char *, uint32_t, uint32_t, uint32_t)) dlsym(v0,
																				 "BASS_StreamCreateURL");
	BASS_StreamCreate = (int (*)(uint32_t, uint32_t, uint32_t, STREAMPROC *, void *)) dlsym(v0,
																							"BASS_StreamCreate");
	BASS_ChannelPlay = (int (*)(uint32_t, bool)) dlsym(v0, "BASS_ChannelPlay");
	BASS_ChannelPause = (int (*)(uint32_t)) dlsym(v0, "BASS_ChannelPause");
	BASS_StreamFree = (int (*)(uint32_t)) dlsym(v0, "BASS_StreamFree");
	BASS_ErrorGetCode = (int (*)(void)) dlsym(v0, "BASS_ErrorGetCode");
	BASS_Set3DFactors = (int (*)(float, float, float)) dlsym(v0, "BASS_Set3DFactors");
	BASS_Set3DPosition = (int (*)(const BASS_3DVECTOR *, const BASS_3DVECTOR *,
								  const BASS_3DVECTOR *, const BASS_3DVECTOR *)) dlsym(v0,
																					   "BASS_Set3DPosition");
	BASS_Apply3D = (int (*)(void)) dlsym(v0, "BASS_Apply3D");
	BASS_ChannelSetFX = (int (*)(uint32_t, HFX)) dlsym(v0, "BASS_ChannelSetFX");
	BASS_ChannelRemoveFX = (int (*)(uint32_t, HFX)) dlsym(v0, "BASS_ChannelRemoveFX");
	BASS_FXSetParameters = (int (*)(HFX, const void *)) dlsym(v0, "BASS_FXSetParameters");
	BASS_IsStarted = (int (*)(void)) dlsym(v0, "BASS_IsStarted");
	BASS_RecordGetDeviceInfo = (int (*)(uint32_t, BASS_DEVICEINFO *)) dlsym(v0,
																			"BASS_RecordGetDeviceInfo");
	BASS_RecordInit = (int (*)(int)) dlsym(v0, "BASS_RecordInit");
	BASS_RecordGetDevice = (int (*)(void)) dlsym(v0, "BASS_RecordGetDevice");
	BASS_RecordFree = (int (*)(void)) dlsym(v0, "BASS_RecordFree");
	BASS_RecordStart = (int (*)(uint32_t, uint32_t, uint32_t, RECORDPROC *, void *)) dlsym(v0,
																						   "BASS_RecordStart");
	BASS_ChannelSetAttribute = (int (*)(uint32_t, uint32_t, float)) dlsym(v0,
																		  "BASS_ChannelSetAttribute");
	BASS_ChannelGetData = (int (*)(uint32_t, void *, uint32_t)) dlsym(v0, "BASS_ChannelGetData");
	BASS_RecordSetInput = (int (*)(int, uint32_t, float)) dlsym(v0, "BASS_RecordSetInput");
	BASS_StreamPutData = (int (*)(uint32_t, const void *, uint32_t)) dlsym(v0,
																		   "BASS_StreamPutData");
	BASS_ChannelSetPosition = (int (*)(uint32_t, uint64_t, uint32_t)) dlsym(v0,
																			"BASS_ChannelSetPosition");
	BASS_ChannelIsActive = (int (*)(uint32_t)) dlsym(v0, "BASS_ChannelIsActive");
	BASS_ChannelSlideAttribute = (int (*)(uint32_t, uint32_t, float, uint32_t)) dlsym(v0,
																					  "BASS_ChannelSlideAttribute");
	BASS_ChannelSet3DAttributes = (int (*)(uint32_t, int, float, float, int, int, float)) dlsym(v0,
																								"BASS_ChannelSet3DAttributes");
	BASS_ChannelSet3DPosition = (int (*)(uint32_t, const BASS_3DVECTOR *, const BASS_3DVECTOR *,
										 const BASS_3DVECTOR *)) dlsym(v0,
																	   "BASS_ChannelSet3DPosition");
	BASS_SetVolume = (int (*)(float)) dlsym(v0, "BASS_SetVolume");
	BASS_ChannelGetTags = (const char *(*)(uint32_t, uint32_t)) dlsym(v0, "BASS_ChannelGetTags");
	BASS_ChannelSetSync = (int (*)(uint32_t, uint32_t, uint64_t, SYNCPROC *, void *)) dlsym(v0,
																							"BASS_ChannelSetSync");



}
