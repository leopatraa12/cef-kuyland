#include "jniutil.h"
#include "game/game.h"
#include "../gui/gui.h"
#include "../gui/uisettings.h"
extern CGame *pGame;
extern UI *pUI;
extern bool g_bHudVisible;

extern bool bIsShowPassengerButt;
extern bool bIsShowLockButt;

namespace {
class ScopedJniEnv {
public:
    ScopedJniEnv() : env(nullptr), attached(false) {
        if (!javaVM) {
            return;
        }

        jint status = javaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                attached = true;
            } else {
                env = nullptr;
            }
        } else if (status != JNI_OK) {
            env = nullptr;
        }
    }

    ~ScopedJniEnv() {
        if (attached && javaVM) {
            javaVM->DetachCurrentThread();
        }
    }

    JNIEnv* get() const {
        return env;
    }

private:
    JNIEnv* env;
    bool attached;
};

bool ValidateJavaCall(JNIEnv* env, jobject activity, jmethodID method, const char* name) {
    if (!env) {
        FLog("No JNI env for %s", name);
        return false;
    }
    if (!activity) {
        FLog("No Java activity for %s", name);
        return false;
    }
    if (!method) {
        FLog("No Java method for %s", name);
        return false;
    }
    return true;
}

void ClearJavaException(JNIEnv* env, const char* name) {
    if (!env || !env->ExceptionCheck()) {
        return;
    }
    FLog("Java exception while calling %s", name);
    env->ExceptionDescribe();
    env->ExceptionClear();
}
}

CJavaWrapper::CJavaWrapper(JNIEnv *env, jobject activity)
{
    this->activity = env->NewGlobalRef(activity);

    jclass clas = env->GetObjectClass(activity);
    if(!clas)
    {
        FLog("no clas");
        return;
    }

    s_showTab = env->GetMethodID(clas, "showTab", "()V");
    s_hideTab = env->GetMethodID(clas, "hideTab", "()V");
    s_clearTab = env->GetMethodID(clas, "clearTab", "()V");
    s_setTab = env->GetMethodID(clas, "setTab", "(ILjava/lang/String;II)V");

    s_showLoadingScreen = env->GetMethodID(clas, "showLoadingScreen", "()V");
    s_hideLoadingScreen = env->GetMethodID(clas, "hideLoadingScreen", "()V");

    s_setPauseState = env->GetMethodID(clas, "setPauseState", "(Z)V");

    s_ShowDialog = env->GetMethodID(clas, "showDialog", "(II[B[B[B[B)V");

    s_showInputLayout = env->GetMethodID(clas, "showKeyboard", "()V");
    s_hideInputLayout = env->GetMethodID(clas, "hideKeyboard", "()V");

    s_ShowLogo = env->GetMethodID(clas, "ShowLogo", "(Z)V");
    s_ShowHud = env->GetMethodID(clas, "showhud", "()V");
    s_HideHud = env->GetMethodID(clas, "hidehud", "()V");
    s_UpdateHud = env->GetMethodID(clas, "UpdateHud", "(IIIIII)V");
    s_UpdateWeaponWheel = env->GetMethodID(clas, "UpdateWeaponWheel", "(Ljava/lang/String;)V");

    s_ShowWithoutReset = env->GetMethodID(clas, "showWithoutReset", "()V");
    s_HideWithoutReset = env->GetMethodID(clas, "hideWithoutReset", "()V");

    s_exitGame = env->GetMethodID(clas, "exitGame", "()V");

    s_showEditObject = env->GetMethodID(clas, "showEditObject", "()V");
    s_hideEditObject = env->GetMethodID(clas, "hideEditObject", "()V");
	
	s_PassaGer = env->GetMethodID(clas, "togglePassengerButton", "(Z)V");

	s_LockVehicle  = env->GetMethodID(clas, "toggleLockButton", "(Z)V");

    env->DeleteLocalRef(clas);
}

void CJavaWrapper::ShowKeyboard()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_showInputLayout, "showKeyboard")) {
        return;
    }
    p->CallVoidMethod(activity, s_showInputLayout);
    ClearJavaException(p, "showKeyboard");
}

void CJavaWrapper::HideKeyboard()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_hideInputLayout, "hideKeyboard")) {
        return;
    }
    p->CallVoidMethod(activity, s_hideInputLayout);
    ClearJavaException(p, "hideKeyboard");
}

void CJavaWrapper::ShowLoadingScreen()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_showLoadingScreen, "showLoadingScreen")) {
        return;
    }
    p->CallVoidMethod(activity, s_showLoadingScreen);
    ClearJavaException(p, "showLoadingScreen");
}

void CJavaWrapper::HideLoadingScreen()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_hideLoadingScreen, "hideLoadingScreen")) {
        return;
    }
    p->CallVoidMethod(activity, s_hideLoadingScreen);
    ClearJavaException(p, "hideLoadingScreen");
}

void CJavaWrapper::SetPauseState(bool pause)
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_setPauseState, "setPauseState")) {
        return;
    }
    p->CallVoidMethod(activity, s_setPauseState, pause);
    ClearJavaException(p, "setPauseState");
}

void CJavaWrapper::SetTab(int id, char* names, int score, int pings)
{
    ScopedJniEnv scopedEnv;
    JNIEnv* global_env = scopedEnv.get();

	if (!ValidateJavaCall(global_env, activity, s_setTab, "setTab"))
    {
		return;
	}

	jclass strClass = global_env->FindClass("java/lang/String"); 
	jmethodID ctorID = global_env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V"); 
	jstring encoding = global_env->NewStringUTF("UTF-8"); 

	jbyteArray bytes = global_env->NewByteArray(strlen(names)); 
	global_env->SetByteArrayRegion(bytes, 0, strlen(names), (jbyte*)names); 
	jstring str1 = (jstring)global_env->NewObject(strClass, ctorID, bytes, encoding);

    global_env->CallVoidMethod(activity, s_setTab, id, str1, score, pings);

    ClearJavaException(global_env, "setTab");
}

void CJavaWrapper::ShowTab()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_showTab, "showTab")) {
        return;
    }
    p->CallVoidMethod(activity, s_showTab);
    ClearJavaException(p, "showTab");
}

void CJavaWrapper::HideTab()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_hideTab, "hideTab")) {
        return;
    }
    p->CallVoidMethod(activity, s_hideTab);
    ClearJavaException(p, "hideTab");
}

void CJavaWrapper::ClearTab()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* p = scopedEnv.get();
    if (!ValidateJavaCall(p, activity, s_clearTab, "clearTab")) {
        return;
    }
    p->CallVoidMethod(activity, s_clearTab);
    ClearJavaException(p, "clearTab");
}

void CJavaWrapper::ShowDialog(int dialogStyle, int dialogID, char* title, char* text, char* button1, char* button2)
{
	ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

	if (!ValidateJavaCall(env, activity, s_ShowDialog, "showDialog"))
	{
		return;
	}

	std::string sTitle(title);
	std::string sText(text);
	std::string sButton1(button1);
	std::string sButton2(button2);

	jbyteArray jstrTitle = as_byte_array((unsigned char*)sTitle.c_str(), sTitle.length());
	jbyteArray jstrText = as_byte_array((unsigned char*)sText.c_str(), sText.length());
	jbyteArray jstrButton1 = as_byte_array((unsigned char*)sButton1.c_str(), sButton1.length());
	jbyteArray jstrButton2 = as_byte_array((unsigned char*)sButton2.c_str(), sButton2.length());

	env->CallVoidMethod(activity, s_ShowDialog, dialogID, dialogStyle, jstrTitle, jstrText, jstrButton1, jstrButton2);

	ClearJavaException(env, "showDialog");
}

void CJavaWrapper::ShowLogo(bool show)
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_ShowLogo, "ShowLogo"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_ShowLogo,show);

    ClearJavaException(env, "ShowLogo");
}

void CJavaWrapper::ShowHud()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_ShowHud, "showhud"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_ShowHud);
    if (pGame) {
        // mostra o radar junto do HUD do Android
        pGame->DisplayHUD(true);
    }
    g_bHudVisible = true;

    ClearJavaException(env, "showhud");
}

void CJavaWrapper::HideHud()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_HideHud, "hidehud"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_HideHud);
    if (pGame) {
        pGame->DisplayHUD(false);
    }
    g_bHudVisible = false;

    ClearJavaException(env, "hidehud");
}

void CJavaWrapper::UpdateHud(int hp, int armour, int eat, int money,int gunId,int ammo)
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_UpdateHud, "UpdateHud"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_UpdateHud,hp ,armour ,eat ,money,gunId,ammo);

    ClearJavaException(env, "UpdateHud");
}

void CJavaWrapper::UpdateWeaponWheel(const char* weaponsJson)
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_UpdateWeaponWheel, "UpdateWeaponWheel"))
    {
        return;
    }

    jstring payload = env->NewStringUTF(weaponsJson ? weaponsJson : "[]");
    env->CallVoidMethod(activity, s_UpdateWeaponWheel, payload);
    env->DeleteLocalRef(payload);

    ClearJavaException(env, "UpdateWeaponWheel");
}

void CJavaWrapper::showWithoutReset()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_ShowWithoutReset, "showWithoutReset"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_ShowWithoutReset);

    ClearJavaException(env, "showWithoutReset");
}

void CJavaWrapper::hideWithoutReset()
{
    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, activity, s_HideWithoutReset, "hideWithoutReset"))
    {
        return;
    }

    env->CallVoidMethod(activity, s_HideWithoutReset);

    ClearJavaException(env, "hideWithoutReset");
}

void CJavaWrapper::exitGame() {

    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, this->activity, this->s_exitGame, "exitGame"))
    {
        return;
    }

    env->CallVoidMethod(this->activity, this->s_exitGame);
    ClearJavaException(env, "exitGame");
}

void CJavaWrapper::ShowEditObject() {

    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, this->activity, this->s_showEditObject, "showEditObject"))
    {
        return;
    }

    env->CallVoidMethod(this->activity, this->s_showEditObject);
    ClearJavaException(env, "showEditObject");
}

void CJavaWrapper::HideEditObject() {

    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();

    if (!ValidateJavaCall(env, this->activity, this->s_hideEditObject, "hideEditObject"))
    {
        return;
    }

    env->CallVoidMethod(this->activity, this->s_hideEditObject);
    ClearJavaException(env, "hideEditObject");
}

void CJavaWrapper::togglePassengerButton(bool toggle)
{
    bIsShowPassengerButt = toggle;

    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();
    if (!ValidateJavaCall(env, activity, s_PassaGer, "togglePassengerButton")) {
        return;
    }

    env->CallVoidMethod(activity, s_PassaGer, toggle);
	
	ClearJavaException(env, "togglePassengerButton");
}

void CJavaWrapper::toggleLockButton(bool toggle)
{
    bIsShowLockButt = toggle;

    ScopedJniEnv scopedEnv;
    JNIEnv* env = scopedEnv.get();
    if (!ValidateJavaCall(env, activity, s_LockVehicle, "toggleLockButton")) {
        return;
    }

    env->CallVoidMethod(activity, s_LockVehicle, toggle);
	
	ClearJavaException(env, "toggleLockButton");
}
