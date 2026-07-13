package com.xyron.game.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.util.DisplayMetrics;
import android.util.TypedValue;
//import android.media.SoundPool;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.akazuki.sampmobilecef.CefClientManager;
import com.akazuki.sampmobilecef.CefJavaManager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.main.ui.AttachEdit;
import com.xyron.game.main.ui.CustomKeyboard;
import com.xyron.game.main.ui.PickupCreatorOverlay;
import com.xyron.game.main.ui.dialog.DialogManager;
import com.xyron.game.main.ui.RadialMenu;
import com.xyron.game.main.ui.RadialVehicles;
import com.xyron.game.main.ui.Radinho;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

//API
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.os.AsyncTask;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.util.Scanner;
import java.lang.reflect.Method;

//WEBVIEW
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

import com.xyron.game.launcher.util.ConfigValidator;
import com.xyron.game.launcher.util.HostShellEngine;
import com.xyron.game.launcher.util.ServerConfigManager;
import org.ini4j.Wini;

public class SAMP extends com.raiferoleplay.game.game.SAMP implements CustomKeyboard.InputListener, HeightProvider.HeightListener {
    private static final String TAG = "SAMP";
    public static final String EXTRA_SERVER_IP = "server_ip";
    public static final String EXTRA_SERVER_PORT = "server_port";
    public static final String EXTRA_NICKNAME = "nickname";
    private static final String PHONE_OVERLAY_URL = "file:///android_asset/interfaces/celular/index.html";
    private static final String INVENTORY_OVERLAY_URL = "file:///android_asset/interfaces/inventario/index.html";
    private static final String MAP_OVERLAY_URL = "file:///android_asset/interfaces/map/index.html";
    private static final String WEAPON_WHEEL_OVERLAY_URL = "file:///android_asset/interfaces/weapon_wheel/index.html";
    private static final long AUTOMATIC_PAUSE_RESUME_SUPPRESSION_RELEASE_DELAY_MS = 1600L;
    private static final long[] NATIVE_MENU_RESUME_AFTER_BACKGROUND_DELAYS_MS = new long[]{220L, 760L, 1420L, 2200L};
    private static final long NATIVE_MENU_RESUME_AFTER_SYSTEM_UI_DELAY_MS = 450L;
    private static final long NATIVE_SETTINGS_FIRST_TAP_DELAY_MS = 380L;
    private static final long NATIVE_SETTINGS_SECOND_TAP_DELAY_MS = 720L;
    private static final long PHONE_OVERLAY_WARMUP_DELAY_MS = 2600L;
    private static final long PHONE_OVERLAY_WARMUP_LOW_RAM_DELAY_MS = 5200L;
    private static final long INVENTORY_OVERLAY_WARMUP_DELAY_MS = 4600L;
    private static final long INVENTORY_OVERLAY_LOW_RAM_DELAY_MS = 7200L;
    private static final boolean VERBOSE_OVERLAY_LOGS = false;
    private static final float NATIVE_MENU_RESUME_X_RATIO = 0.50f;
    private static final float NATIVE_MENU_RESUME_Y_RATIO = 0.893f;
    private static final float NATIVE_MENU_SETTINGS_X_RATIO = 0.631f;
    private static final float NATIVE_MENU_SETTINGS_Y_RATIO = 0.762f;
    private static final boolean USE_NATIVE_IMGUI_OVERLAYS = false;
    private static final int NATIVE_OVERLAY_NONE = 0;
    private static final int NATIVE_OVERLAY_PHONE = 1;
    private static final int NATIVE_OVERLAY_INVENTORY = 2;
    private static final int VOICE_COMMAND_REQUEST = 4402;
    private static final String HUD_RENDER_PREFS = "xyron_hud_render_settings";
    private static final int HUD_QUALITY_FPS = 0;
    private static final int HUD_QUALITY_BALANCED = 1;
    private static final int HUD_QUALITY_HIGH = 2;
    private static final int HUD_RENDER_DISTANCE_MIN = 30;
    private static final int HUD_RENDER_DISTANCE_MAX = 160;
    private static final int HUD_FPS_LIMIT_MIN = 30;
    private static final int HUD_FPS_LIMIT_MAX = 120;
    private static SAMP instance;

    native void sendCommandV(byte[] str);
    native void setNativeOverlayState(int overlayType);
    native int getNativeOverlayState();
    native void setAllowNextNativePauseMenu(boolean allow);
    native void forceEndNativeUserPause();
    native void sendSyntheticNativeTouch(int x, int y);
    native float[] getPlayerPlacementSnapshot();
    native boolean showLocalPickupPreview(int modelId, int pickupType, float x, float y, float z);
    native void selectWeapon(int weaponId);

    private CustomKeyboard mKeyboard;
    private DialogManager mDialog;
    private HeightProvider mHeightProvider;
    private PickupCreatorOverlay mPickupCreatorOverlay;

    //webviewcell
    private WebView webcell;
    private FrameLayout framewebcell;
    private WebView webInventory;
    private FrameLayout framewebInventory;
    private WebView webMiniMap;
    private FrameLayout framewebMiniMap;
    private WebView webWeaponWheel;
    private FrameLayout framewebWeaponWheel;
    private RuntimeOverlayBridge runtimeOverlayBridge;
    private View overlayBlurScrim;
    private WebView pendingVoiceTarget;
    private boolean runtimeLowRamMode;
    private boolean phoneOverlayConfigured;
    private boolean inventoryOverlayConfigured;
    private boolean mapOverlayConfigured;
    private boolean weaponWheelOverlayConfigured;
    private boolean receivedNativeWeaponWheelSnapshot;
    private String lastWeaponWheelJson = "[{\"id\":0,\"ammo\":0,\"current\":true}]";
    private View hudSettingsPanel;
    private TextView hudSettingsSummary;
    private TextView hudRenderDistanceValue;
    private TextView hudFpsLimitValue;
    private TextView hudQualityLowButton;
    private TextView hudQualityBalancedButton;
    private TextView hudQualityHighButton;
    private TextView hudShadowsToggle;
    private TextView hudEffectsToggle;
    private SeekBar hudRenderDistanceSeek;
    private SeekBar hudFpsLimitSeek;
    private int hudRenderDistance = 70;
    private int hudFpsLimit = 60;
    private int hudQuality = HUD_QUALITY_BALANCED;
    private boolean hudShadowsEnabled = true;
    private boolean hudEffectsEnabled = true;
    private Runnable phoneOverlayWarmupRunnable;
    private Runnable inventoryOverlayWarmupRunnable;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean pendingSystemUiMenuDismiss;
    private boolean pendingBackgroundMenuDismiss;
    private int backgroundMenuDismissStep;
    private boolean backgroundReturnDismissArmed;
    private boolean suppressAutomaticNativePauseResume;
    private boolean skipNextAutomaticLifecycleResumeEvent;
    private volatile boolean localHostBootstrapRequested;
    private final Runnable clearAutomaticPauseResumeSuppressionRunnable = new Runnable() {
        @Override
        public void run() {
            suppressAutomaticNativePauseResume = false;
        }
    };
    private final Runnable dismissMenuAfterSystemUiRunnable = new Runnable() {
        @Override
        public void run() {
            pendingSystemUiMenuDismiss = false;
            if (!shouldHandleSystemUiMapMenu()) {
                return;
            }
            performNativeMenuTap(NATIVE_MENU_RESUME_X_RATIO, NATIVE_MENU_RESUME_Y_RATIO);
        }
    };
    private final Runnable dismissMenuAfterBackgroundRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "backgroundMenuDismiss step=" + backgroundMenuDismissStep + " pending=" + pendingBackgroundMenuDismiss);
            if (!shouldAutoDismissNativeMenu()) {
                pendingBackgroundMenuDismiss = false;
                backgroundMenuDismissStep = 0;
                return;
            }
            requestNativeGameplayResume();
            performNativeMenuTap(NATIVE_MENU_RESUME_X_RATIO, NATIVE_MENU_RESUME_Y_RATIO);
            backgroundMenuDismissStep += 1;
            if (!pendingBackgroundMenuDismiss
                    || backgroundMenuDismissStep >= NATIVE_MENU_RESUME_AFTER_BACKGROUND_DELAYS_MS.length) {
                pendingBackgroundMenuDismiss = false;
                backgroundMenuDismissStep = 0;
                return;
            }

            long previousDelay = NATIVE_MENU_RESUME_AFTER_BACKGROUND_DELAYS_MS[backgroundMenuDismissStep - 1];
            long nextDelay = NATIVE_MENU_RESUME_AFTER_BACKGROUND_DELAYS_MS[backgroundMenuDismissStep];
            uiHandler.postDelayed(
                    dismissMenuAfterBackgroundRunnable,
                    nextDelay - previousDelay
            );
        }
    };

    //public static SoundPool soundPool = null;
    private AttachEdit mAttachEdit;
    private RadialMenu mRadialMenu;
    private RadialVehicles mRadialVehicles;
    private Radinho mRadinho;

    ConstraintLayout hud_main;
    ConstraintLayout loadingscreen;

    private int iShowHud;
    private boolean iShowLogo;
    private boolean TeclasAbertas;
    private CefJavaManager mJavaManager = null;
    private CefClientManager mClientManager = null;
    //API
    private View connectScreenView;
    private View LoginScreenView;
    private View RegisterScreenView;
    private Handler handler;
    private Runnable apiCheckerRunnable;

    //WEBVIEW
    private View WebViewScreenView;

    long buttonLockCD;

    static String vmVersion;

    static {
        vmVersion = null;
        Log.i(TAG, "**** Loading SO's");

        try {
            vmVersion = System.getProperty("java.vm.version");
            Log.i(TAG, "vmVersion " + vmVersion);

            System.loadLibrary("bass");
            int shadowHookInit = com.bytedance.shadowhook.ShadowHook.init(
                    new com.bytedance.shadowhook.ShadowHook.ConfigBuilder()
                            .setMode(com.bytedance.shadowhook.ShadowHook.Mode.SHARED)
                            .setDebuggable(true)
                            .setRecordable(true)
                            .build()
            );
            Log.i(TAG, "ShadowHook init result " + shadowHookInit);
            System.loadLibrary("SAMP");
        }
        catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public native void sendDialogResponse(int i, int i2, int i3, byte[] str);

    public static SAMP getInstance() {
        return instance;
    }

    private void showTab()
    {

    }

    private void hideTab()
    {

    }

    private void setTab(int id, String name, int score, int ping)
    {

    }

    private void clearTab()
    {

    }

    private void showLoadingScreen()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingscreen.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideLoadingScreen()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingscreen.setVisibility(View.GONE);
                //exibirTelaDeConexaoEVerificarAPI();
                MostrarChat();
            }
        });
    }

  private void setPauseState(boolean pause) {
    runOnUiThread(() -> {
        // Oculta/exibe a UI do sistema
        if (pause) {
            hideSystemUI();
        } else {
            showSystemUI();
        }

        // Oculta/exibe a WebView do CEF
        CefJavaManager mJavaManager = null;
        if (mJavaManager != null && mJavaManager.isShow()) {
            if (pause)
                mJavaManager.hideBrowserView();
            else
                mJavaManager.showBrowserView();
        }
    });
}


    public void exitGame(){
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);

        finishAndRemoveTask();
        System.exit(0);
    }

    public void handleConnectionFailure(String host, int port, int attempts) {
        runOnUiThread(() -> {
            destroyRuntimeOverlays();

            String address = (host == null || host.trim().isEmpty())
                    ? "servidor desconhecido"
                    : host + ":" + port;
            Toast.makeText(
                    this,
                    "Nao foi possivel conectar em " + address + ". Voltando ao launcher.",
                    Toast.LENGTH_LONG
            ).show();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        final String caption = new String(bArr, StandardCharsets.UTF_8);
        final String content = new String(bArr2, StandardCharsets.UTF_8);
        final String leftBtnText = new String(bArr3, StandardCharsets.UTF_8);
        final String rightBtnText = new String(bArr4, StandardCharsets.UTF_8);
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }

    public void hideWithoutReset()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hud_main.setVisibility(View.GONE);
                mDialog.hideWithoutReset();
                mAttachEdit.hideWithoutReset();
            }
        });
    }

    public void showWithoutReset()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(iShowHud == 1)
                    hud_main.setVisibility(View.VISIBLE);
                if(mAttachEdit.isShow)
                    mAttachEdit.showWithoutReset();
                if(mDialog.isShow)
                    mDialog.showWithOldContent();
            }
        });
    }

    private void showEditObject()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAttachEdit.show();
            }
        });
    }

    private void hideEditObject()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAttachEdit.hide();
            }
        });
    }

    @Override
    public void OnInputEnd(String str)
    {
        if (handleLocalRuntimeCommand(str)) {
            return;
        }

        byte[] toReturn = null;
        try
        {
            toReturn = str.getBytes("windows-1251");
        }
        catch(UnsupportedEncodingException e)
        {

        }

        try {
            onInputEnd(toReturn);
        }
        catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }
    }

    private void showKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("AXL", "showKeyboard()");
                mKeyboard.ShowInputLayout();
            }
        });
    }

    private void hideKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mKeyboard.HideInputLayout();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "**** onCreate");

        // Keep the mobile HUD layout files in sync before the engine reads them.
        ConfigValidator.validateConfigFiles(this);

        super.onCreate(savedInstanceState);
        
        //API
        handler = new Handler(Looper.getMainLooper());
        runtimeLowRamMode = detectLowRamMode();
        mHeightProvider = new HeightProvider(this);

        mDialog = new DialogManager(this);

        mAttachEdit = new AttachEdit(this);

        mRadialMenu = new RadialMenu(this);

        mRadinho = new Radinho(this);

        hud_main = (ConstraintLayout) getLayoutInflater().inflate(R.layout.hud, null);
        addContentView(hud_main, new ConstraintLayout.LayoutParams(-1, -1));
        hud_main.setVisibility(View.GONE);
        initializeRuntimeOverlays();

        loadingscreen = (ConstraintLayout) getLayoutInflater().inflate(R.layout.loading_screen, null);
        addContentView(loadingscreen, new ConstraintLayout.LayoutParams(-1, -1));
        loadingscreen.setVisibility(View.GONE);

        mKeyboard = new CustomKeyboard(this);
        mPickupCreatorOverlay = new PickupCreatorOverlay(this, new PickupCreatorOverlay.Listener() {
            @Override
            public float[] captureCurrentPlacement() {
                try {
                    return getPlayerPlacementSnapshot();
                } catch (UnsatisfiedLinkError error) {
                    Log.e(TAG, "Nao foi possivel capturar a posicao atual do player.", error);
                    return null;
                }
            }

            @Override
            public boolean previewPickup(com.xyron.game.launcher.util.PickupStudioManager.PickupDefinition pickup) {
                if (pickup == null) {
                    return false;
                }
                try {
                    return showLocalPickupPreview(
                            pickup.modelId,
                            pickup.pickupType,
                            pickup.x,
                            pickup.y,
                            pickup.z
                    );
                } catch (UnsatisfiedLinkError error) {
                    Log.e(TAG, "Nao foi possivel criar o preview local do pickup.", error);
                    return false;
                }
            }
        });

        /*AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setAudioAttributes(attributes).build();*/

        instance = this;
        //WEBVIEW
        // PrÃ©-inicializa o processo do WebView Chromium
        // InicializaÃ§Ã£o leve do WebView no inÃ­cio
        /*framewebcell = findViewById(R.id.framewebcell);
        webcell = findViewById(R.id.webcell);
        webcell.post(() -> {
            webcell.setBackgroundColor(Color.TRANSPARENT);
            webcell.getSettings().setJavaScriptEnabled(true);
            webcell.getSettings().setDomStorageEnabled(true);
            webcell.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

            // Desativa o zoom direto sem criar variÃ¡vel:
            webcell.getSettings().setSupportZoom(false);
            webcell.getSettings().setBuiltInZoomControls(false);
            webcell.getSettings().setDisplayZoomControls(false);

            // Bloqueia pinch-to-zoom de verdade
            webcell.getSettings().setUseWideViewPort(true);
            webcell.getSettings().setLoadWithOverviewMode(true);
            webcell.setInitialScale(100);

            webcell.setWebViewClient(new WebViewClient());
            webcell.loadUrl("file:///android_asset/interfaces/celular/index.html");  // Carregamento REAL antecipado
        
            webcellcarregada = true; // jÃ¡ marca como carregada

            webcell.setOnTouchListener((v, event) -> {
                if (event.getPointerCount() > 1) {
                    return true; // Bloqueia multi-toque (zoom com 2 dedos)
                }
                return false;
            });
        });
        webcell.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        framewebcell.setVisibility(View.GONE);
        webcell.setVisibility(View.GONE);*/

        applyDirectConnectExtras(getIntent());
        ensureLocalHostRuntimeBeforeNativeConnect();

        try {
            initializeSAMP();
            // Recovered native builds arm g_bPlaySAMP on the first call; replay once so StartGame sees it.
            initializeSAMP();
            setAllowNextNativePauseMenu(false);
        } catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }
        hideSystemUI();
        installSystemUiWatcher();

    }

    private void applyDirectConnectExtras(Intent intent) {
        if (intent == null) {
            return;
        }

        String receivedHost = sanitizeDirectConnectHost(intent.getStringExtra(EXTRA_SERVER_IP));
        int receivedPort = intent.getIntExtra(EXTRA_SERVER_PORT, 0);
        String receivedNickname = sanitizeDirectConnectNickname(intent.getStringExtra(EXTRA_NICKNAME));

        if (receivedHost.length() > 0 && receivedPort > 0 && receivedPort <= 65535) {
            ServerConfigManager.ServerOption option = ServerConfigManager.addOrUpdateServer(
                    this,
                    "Direct Connect",
                    receivedHost,
                    receivedPort,
                    true
            );

            if (option == null || !option.isValid()) {
                java.util.List<ServerConfigManager.ServerOption> servers = ServerConfigManager.getAvailableServers(this);
                if (!servers.isEmpty()) {
                    ServerConfigManager.removeServer(this, servers.get(servers.size() - 1));
                    option = ServerConfigManager.addOrUpdateServer(
                            this,
                            "Direct Connect",
                            receivedHost,
                            receivedPort,
                            true
                    );
                }
            }

            if (option == null || !option.isValid()) {
                option = new ServerConfigManager.ServerOption("Direct Connect", receivedHost, receivedPort, true);
            }

            boolean saved = ServerConfigManager.saveSelectedServer(this, option);
            Log.i(TAG, "Direct connect target received: " + receivedHost + ":" + receivedPort + " saved=" + saved);
        }

        if (receivedNickname.length() > 0) {
            saveDirectConnectNickname(receivedNickname);
        }
    }

    private String sanitizeDirectConnectHost(String host) {
        return host == null ? "" : host.trim();
    }

    private String sanitizeDirectConnectNickname(String nickname) {
        if (nickname == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        String trimmed = nickname.trim();
        for (int i = 0; i < trimmed.length() && builder.length() < 24; i++) {
            char value = trimmed.charAt(i);
            boolean accepted = (value >= 'A' && value <= 'Z')
                    || (value >= 'a' && value <= 'z')
                    || (value >= '0' && value <= '9')
                    || value == '_'
                    || value == '['
                    || value == ']'
                    || value == '('
                    || value == ')'
                    || value == '.'
                    || value == '$'
                    || value == '='
                    || value == '@';
            if (accepted) {
                builder.append(value);
            }
        }
        return builder.length() > 0 ? builder.toString() : "Developer_Player";
    }

    private void saveDirectConnectNickname(String nickname) {
        File settingsFile = new File(getExternalFilesDir(null), "SAMP/settings.ini");
        File parent = settingsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Could not create SAMP settings directory for direct nickname.");
            return;
        }

        try {
            if (!settingsFile.exists() && !settingsFile.createNewFile()) {
                Log.w(TAG, "Could not create settings.ini for direct nickname.");
                return;
            }

            Wini wini = new Wini(settingsFile);
            wini.put("client", "name", nickname);
            wini.store();
            Log.i(TAG, "Direct connect nickname applied: " + nickname);
        } catch (IOException e) {
            Log.e(TAG, "Failed to persist direct connect nickname.", e);
        }
    }

    private void ensureLocalHostRuntimeBeforeNativeConnect() {
        ServerConfigManager.ServerOption option = ServerConfigManager.getSelectedServer(this);
        if (option == null || !option.isValid()) {
            return;
        }
        boolean localLoopback = "127.0.0.1".equals(option.host) || "localhost".equalsIgnoreCase(option.host);
        if (!localLoopback || option.port != 7777) {
            return;
        }
        if (HostShellEngine.isHostReady(this)
                || HostShellEngine.isHostRunning(this)
                || HostShellEngine.isHostStarting(this)) {
            return;
        }
        if (localHostBootstrapRequested) {
            return;
        }
        localHostBootstrapRequested = true;

        Context appContext = getApplicationContext();
        new Thread(() -> {
            HostShellEngine.CommandResult result = HostShellEngine.bootHost(appContext);
            if (result == null || !result.success) {
                runOnUiThread(() -> Toast.makeText(
                        SAMP.this,
                        extractLocalHostRuntimeMessage(result),
                        Toast.LENGTH_LONG
                ).show());
            }
        }, "xyron-local-host-bootstrap").start();
    }

    private String extractLocalHostRuntimeMessage(HostShellEngine.CommandResult result) {
        if (result == null || result.output == null || result.output.trim().isEmpty()) {
            return "Nao foi possivel ligar o host local antes do jogo.";
        }
        String[] lines = result.output.trim().split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || "Fluxo rapido do host".equalsIgnoreCase(line)) {
                continue;
            }
            String normalized = line.toLowerCase(Locale.US);
            if (normalized.contains("falha")
                    || normalized.contains("erro")
                    || normalized.contains("processo saiu")
                    || normalized.contains("nao foi possivel")) {
                return line;
            }
        }
        return "Host local preparado para o jogo.";
    }
     public void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void installSystemUiWatcher() {
        View decorView = getWindow() != null ? getWindow().getDecorView() : null;
        if (decorView == null) {
            return;
        }

        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            boolean fullscreenVisible = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            if (!fullscreenVisible) {
                pendingSystemUiMenuDismiss = shouldHandleSystemUiMapMenu();
                return;
            }
            hideSystemUI();
            if (!pendingSystemUiMenuDismiss) {
                return;
            }
            uiHandler.removeCallbacks(dismissMenuAfterSystemUiRunnable);
            uiHandler.postDelayed(
                    dismissMenuAfterSystemUiRunnable,
                    NATIVE_MENU_RESUME_AFTER_SYSTEM_UI_DELAY_MS
            );
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        boolean ignoredTransientLoss = !hasFocus && shouldIgnoreTransientFocusLoss();
        if (ignoredTransientLoss) {
            hideSystemUI();
            return;
        }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
            if (suppressAutomaticNativePauseResume) {
                scheduleAutomaticPauseResumeSuppressionRelease();
            }
            if (pendingBackgroundMenuDismiss) {
                scheduleBackgroundMenuDismissSequence();
            }
        }
    }
    public native void togglePlayer(int toggle);

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyDirectConnectExtras(intent);
    }


    @Override
    public void onStart() {
        Log.i(TAG, "**** onStart");
        super.onStart();
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "**** onRestart");
        backgroundReturnDismissArmed = true;
        pendingBackgroundMenuDismiss = true;
        super.onRestart();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "**** onResume");
        boolean suppressAutomaticResume = skipNextAutomaticLifecycleResumeEvent;
        boolean originalResumeEventDone = ResumeEventDone;
        if (suppressAutomaticResume) {
            ResumeEventDone = false;
        }
        try {
            super.onResume();
        } finally {
            ResumeEventDone = originalResumeEventDone;
            skipNextAutomaticLifecycleResumeEvent = false;
        }
        mHeightProvider.init(view);
        if (suppressAutomaticNativePauseResume) {
            scheduleAutomaticPauseResumeSuppressionRelease();
        }
        if (backgroundReturnDismissArmed) {
            backgroundReturnDismissArmed = false;
            pendingBackgroundMenuDismiss = true;
            requestNativeGameplayResume();
            scheduleBackgroundMenuDismissSequence();
        } else if (pendingBackgroundMenuDismiss) {
            scheduleBackgroundMenuDismissSequence();
        }
    }

    native void onClickButton(int action);
    //onClickButton(2);

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (!handleRuntimeBack()) {
                        onEventBackPressed();
                        hideSystemUI();
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    hideSystemUI();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (handleRuntimeBack()) {
            return;
        }
        onEventBackPressed();
        hideSystemUI();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (handleRuntimeBack()) {
                return true;
            }
            onEventBackPressed();
            hideSystemUI();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            hideSystemUI();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "**** onPause");
        boolean suppressAutomaticPause = shouldSuppressAutomaticPauseMenu();
        suppressAutomaticNativePauseResume = suppressAutomaticPause;
        skipNextAutomaticLifecycleResumeEvent = suppressAutomaticPause;
        pendingBackgroundMenuDismiss = shouldAutoDismissNativeMenu();
        backgroundMenuDismissStep = 0;
        uiHandler.removeCallbacks(clearAutomaticPauseResumeSuppressionRunnable);
        uiHandler.removeCallbacks(dismissMenuAfterBackgroundRunnable);

        boolean originalResumeEventDone = ResumeEventDone;
        if (suppressAutomaticPause) {
            ResumeEventDone = false;
        }
        try {
            super.onPause();
        } finally {
            ResumeEventDone = originalResumeEventDone;
        }
    }

    @Override
    public void onStop() {
        Log.i(TAG, "**** onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "**** onDestroy");
        cancelOverlayWarmups();
        uiHandler.removeCallbacks(clearAutomaticPauseResumeSuppressionRunnable);
        uiHandler.removeCallbacks(dismissMenuAfterBackgroundRunnable);
        uiHandler.removeCallbacks(dismissMenuAfterSystemUiRunnable);
        destroyRuntimeOverlays();
        super.onDestroy();
    }

    @Override
    public void onHeightChanged(int orientation, int height) {
        mKeyboard.onHeightChanged(height);
        mDialog.onHeightChanged(height);
    }

    public ConstraintLayout logo;

    public ConstraintLayout hud1;
    private ProgressBar hpBar;
    private ProgressBar armourBar;
    private ProgressBar eatBar;
    private ProgressBar sedeBar;
    private ProgressBar sonoBar;
    private TextView moneyText;
    private TextView hpText;
    private TextView armourText;
    private ImageView gunImg;
    private TextView ammoText;
    private ImageView enter_passengerB;
    private ImageView lock_vehicle;

    public ConstraintLayout hud_y_v;
    public ConstraintLayout hud_f_v;

    public void ShowLogo(boolean show)
    {
        iShowLogo = show;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    public void togglePassengerButton(boolean toggle)
    {
        if(toggle)
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    if(hud_main == null) return;
                    enter_passengerB = hud_main.findViewById(R.id.enter_passenger);
                    enter_passengerB.setVisibility(View.VISIBLE);
                }
            });
        }
        else
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    if(hud_main == null) return;
                    enter_passengerB = hud_main.findViewById(R.id.enter_passenger);
                    enter_passengerB.setVisibility(View.INVISIBLE);
                }
            });
        }
    }
    void toggleLockButton(boolean toggle)
    {
        if(toggle)
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    if(hud_main == null) return;
                    lock_vehicle = hud_main.findViewById(R.id.vehicle_lock_butt);
                    lock_vehicle.setVisibility(View.VISIBLE);
                }
            });
        }
        else
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    if(hud_main == null) return;
                    lock_vehicle = hud_main.findViewById(R.id.vehicle_lock_butt);
                    lock_vehicle.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    void MostrarTeclas() {
        if (!TeclasAbertas) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (hud_main == null) return;
                    hud_y_v = hud_main.findViewById(R.id.hud_y);
                    hud_f_v = hud_main.findViewById(R.id.hud_f);
                    hud_y_v.setVisibility(View.VISIBLE);
                    hud_f_v.setVisibility(View.VISIBLE);
                    TeclasAbertas = true;
                }
            });
        }
    }
    void EsconderTeclas()
    {
        if (TeclasAbertas)
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (hud_main == null) return;
                    hud_y_v = hud_main.findViewById(R.id.hud_y);
                    hud_f_v = hud_main.findViewById(R.id.hud_f);
                    hud_y_v.setVisibility(View.INVISIBLE);
                    hud_f_v.setVisibility(View.INVISIBLE);
                    TeclasAbertas = false;
                }
            });
        }
    }
    
    native void ClickLockVehicleButton();
    public native void ClickEnterPassengerButton();
    public native void changeGun();
    native void MostrarChat();

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private void applyHudLeftPanelLayout() {
        if (hud_main == null) return;
        View panel = hud_main.findViewById(R.id.hud_left_panel);
        if (panel == null) return;
        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) panel.getLayoutParams();
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = dpToPx(56.0f);
        lp.bottomMargin = dpToPx(12.0f);
        panel.setLayoutParams(lp);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void initializeHudSettingsPanel() {
        if (hud_main == null) {
            return;
        }

        hudSettingsPanel = hud_main.findViewById(R.id.hud_settings_panel);
        if (hudSettingsPanel == null) {
            return;
        }

        hudSettingsSummary = hud_main.findViewById(R.id.hud_settings_summary);
        hudRenderDistanceValue = hud_main.findViewById(R.id.hud_render_distance_value);
        hudFpsLimitValue = hud_main.findViewById(R.id.hud_fps_limit_value);
        hudQualityLowButton = hud_main.findViewById(R.id.hud_quality_low);
        hudQualityBalancedButton = hud_main.findViewById(R.id.hud_quality_balanced);
        hudQualityHighButton = hud_main.findViewById(R.id.hud_quality_high);
        hudShadowsToggle = hud_main.findViewById(R.id.hud_shadows_toggle);
        hudEffectsToggle = hud_main.findViewById(R.id.hud_effects_toggle);
        hudRenderDistanceSeek = hud_main.findViewById(R.id.hud_render_distance_seek);
        hudFpsLimitSeek = hud_main.findViewById(R.id.hud_fps_limit_seek);

        loadHudRenderSettings();

        if (hudQualityLowButton != null) {
            hudQualityLowButton.setOnClickListener(v -> {
                hudQuality = HUD_QUALITY_FPS;
                syncHudRenderSettingsUi();
            });
        }
        if (hudQualityBalancedButton != null) {
            hudQualityBalancedButton.setOnClickListener(v -> {
                hudQuality = HUD_QUALITY_BALANCED;
                syncHudRenderSettingsUi();
            });
        }
        if (hudQualityHighButton != null) {
            hudQualityHighButton.setOnClickListener(v -> {
                hudQuality = HUD_QUALITY_HIGH;
                syncHudRenderSettingsUi();
            });
        }
        if (hudShadowsToggle != null) {
            hudShadowsToggle.setOnClickListener(v -> {
                hudShadowsEnabled = !hudShadowsEnabled;
                syncHudRenderSettingsUi();
            });
        }
        if (hudEffectsToggle != null) {
            hudEffectsToggle.setOnClickListener(v -> {
                hudEffectsEnabled = !hudEffectsEnabled;
                syncHudRenderSettingsUi();
            });
        }
        if (hudRenderDistanceSeek != null) {
            hudRenderDistanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    hudRenderDistance = clampInt(
                            HUD_RENDER_DISTANCE_MIN + progress,
                            HUD_RENDER_DISTANCE_MIN,
                            HUD_RENDER_DISTANCE_MAX
                    );
                    syncHudRenderSettingsText();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
        if (hudFpsLimitSeek != null) {
            hudFpsLimitSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    hudFpsLimit = clampInt(
                            HUD_FPS_LIMIT_MIN + progress,
                            HUD_FPS_LIMIT_MIN,
                            HUD_FPS_LIMIT_MAX
                    );
                    syncHudRenderSettingsText();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        View closeButton = hud_main.findViewById(R.id.hud_settings_close);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> hideHudSettingsPanel());
        }
        View applyButton = hud_main.findViewById(R.id.hud_settings_apply);
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> {
                saveHudRenderSettings();
                applyHudRenderSettingsRuntime();
                hideHudSettingsPanel();
                Toast.makeText(this, "Renderizacao aplicada.", Toast.LENGTH_SHORT).show();
            });
        }

        syncHudRenderSettingsUi();
        applyHudRenderSettingsRuntime();
    }

    private void loadHudRenderSettings() {
        SharedPreferences preferences = getSharedPreferences(HUD_RENDER_PREFS, MODE_PRIVATE);
        hudRenderDistance = clampInt(
                preferences.getInt("render_distance", hudRenderDistance),
                HUD_RENDER_DISTANCE_MIN,
                HUD_RENDER_DISTANCE_MAX
        );
        hudFpsLimit = clampInt(
                preferences.getInt("fps_limit", hudFpsLimit),
                HUD_FPS_LIMIT_MIN,
                HUD_FPS_LIMIT_MAX
        );
        hudQuality = clampInt(
                preferences.getInt("quality", hudQuality),
                HUD_QUALITY_FPS,
                HUD_QUALITY_HIGH
        );
        hudShadowsEnabled = preferences.getBoolean("shadows", hudShadowsEnabled);
        hudEffectsEnabled = preferences.getBoolean("effects", hudEffectsEnabled);
    }

    private void saveHudRenderSettings() {
        getSharedPreferences(HUD_RENDER_PREFS, MODE_PRIVATE)
                .edit()
                .putInt("render_distance", hudRenderDistance)
                .putInt("fps_limit", hudFpsLimit)
                .putInt("quality", hudQuality)
                .putBoolean("shadows", hudShadowsEnabled)
                .putBoolean("effects", hudEffectsEnabled)
                .apply();
        writeHudRenderSettingsFile();
    }

    private void writeHudRenderSettingsFile() {
        File root = getExternalFilesDir(null);
        if (root == null) {
            return;
        }

        File settingsFile = new File(root, "SAMP/render_settings.ini");
        File parent = settingsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Could not create render settings directory.");
            return;
        }

        try {
            Wini wini = new Wini(settingsFile);
            wini.put("render", "profile", hudQualityLabel());
            wini.put("render", "distance", hudRenderDistance);
            wini.put("render", "fps_limit", hudFpsLimit);
            wini.put("render", "shadows", hudShadowsEnabled ? 1 : 0);
            wini.put("render", "effects", hudEffectsEnabled ? 1 : 0);
            wini.store();
        } catch (IOException error) {
            Log.e(TAG, "Could not store render settings.", error);
        }
    }

    private void applyHudRenderSettingsRuntime() {
        runtimeLowRamMode = detectLowRamMode()
                || hudQuality == HUD_QUALITY_FPS
                || !hudEffectsEnabled;
        if (getWindow() != null) {
            android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredRefreshRate = (float) hudFpsLimit;
            getWindow().setAttributes(params);
        }
        updateRuntimeOverlayExecutionState();
        hideSystemUI();
    }

    private String hudQualityLabel() {
        if (hudQuality == HUD_QUALITY_FPS) {
            return "fps";
        }
        if (hudQuality == HUD_QUALITY_HIGH) {
            return "high";
        }
        return "balanced";
    }

    private void syncHudRenderSettingsUi() {
        if (hudRenderDistanceSeek != null) {
            hudRenderDistanceSeek.setProgress(hudRenderDistance - HUD_RENDER_DISTANCE_MIN);
        }
        if (hudFpsLimitSeek != null) {
            hudFpsLimitSeek.setProgress(hudFpsLimit - HUD_FPS_LIMIT_MIN);
        }
        syncHudRenderSettingsText();
        setHudQualityButtonState(hudQualityLowButton, hudQuality == HUD_QUALITY_FPS);
        setHudQualityButtonState(hudQualityBalancedButton, hudQuality == HUD_QUALITY_BALANCED);
        setHudQualityButtonState(hudQualityHighButton, hudQuality == HUD_QUALITY_HIGH);
        setHudToggleState(hudShadowsToggle, "Sombras", hudShadowsEnabled);
        setHudToggleState(hudEffectsToggle, "Efeitos", hudEffectsEnabled);
    }

    private void syncHudRenderSettingsText() {
        if (hudRenderDistanceValue != null) {
            hudRenderDistanceValue.setText(hudRenderDistance + "m");
        }
        if (hudFpsLimitValue != null) {
            hudFpsLimitValue.setText(hudFpsLimit + " FPS");
        }
        if (hudSettingsSummary != null) {
            String profile = hudQuality == HUD_QUALITY_FPS
                    ? "Perfil FPS"
                    : hudQuality == HUD_QUALITY_HIGH ? "Perfil alto" : "Perfil medio";
            hudSettingsSummary.setText(
                    profile + " | " + hudRenderDistance + "m | " + hudFpsLimit + " FPS"
            );
        }
    }

    private void setHudQualityButtonState(TextView view, boolean selected) {
        if (view == null) {
            return;
        }
        view.setBackgroundResource(selected
                ? R.drawable.home_server_select_button
                : R.drawable.launcher_field_surface_dark);
        view.setTextColor(Color.parseColor(selected ? "#FFFFFFFF" : "#D4DEE4"));
    }

    private void setHudToggleState(TextView view, String label, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setText(label + ": " + (enabled ? "ON" : "OFF"));
        view.setBackgroundResource(enabled
                ? R.drawable.home_server_select_button
                : R.drawable.launcher_field_surface_dark);
        view.setTextColor(Color.parseColor(enabled ? "#FFFFFFFF" : "#D4DEE4"));
    }

    private boolean isHudSettingsVisible() {
        return hudSettingsPanel != null && hudSettingsPanel.getVisibility() == View.VISIBLE;
    }

    private void showHudSettingsPanel() {
        initializeHudSettingsPanel();
        if (hudSettingsPanel == null) {
            Toast.makeText(this, "Painel de configuracao indisponivel.", Toast.LENGTH_SHORT).show();
            return;
        }
        hidePhoneOverlayInternal();
        hideInventoryOverlayInternal();
        hideMapOverlayInternal();
        hideWeaponWheelOverlayInternal();
        if (RadialMenu.menuVisible && mRadialMenu != null) {
            mRadialMenu.hide();
        }
        if (Radinho.radinhoVisible && mRadinho != null) {
            mRadinho.hide();
        }
        hudSettingsPanel.bringToFront();
        hudSettingsPanel.setVisibility(View.VISIBLE);
        refreshRuntimeChrome();
        hideSystemUI();
    }

    private void hideHudSettingsPanel() {
        if (hudSettingsPanel != null) {
            hudSettingsPanel.setVisibility(View.GONE);
        }
        refreshRuntimeChrome();
        hideSystemUI();
    }

    private int clampHudStatus(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private void setCircularStatusProgress(ProgressBar progressBar, int value) {
        if (progressBar == null) {
            return;
        }
        int safeValue = clampHudStatus(value);
        progressBar.setMax(100);
        progressBar.setProgress(safeValue);
        if (progressBar.getProgressDrawable() != null) {
            progressBar.getProgressDrawable().setLevel(safeValue * 100);
        }
    }

    public void showhud()
    {
        iShowHud = 1;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(hud_main == null) return;
                hud_main.setVisibility(View.VISIBLE);
                hud1 = hud_main.findViewById(R.id.hud);

                hud1.setVisibility(View.VISIBLE);
                hud1.setAlpha(1.0f);
                applyHudLeftPanelLayout();
                initializeHudSettingsPanel();

                //TESTEWEBVIEW
                //ShowCell();

                // BotÃ£o para sentar como passageiro
                enter_passengerB = hud_main.findViewById(R.id.enter_passenger);
                enter_passengerB.setVisibility(View.INVISIBLE);

                // BotÃ£o para trancar e destrancar
                lock_vehicle = hud_main.findViewById(R.id.vehicle_lock_butt);
                lock_vehicle.setVisibility(View.INVISIBLE);

                hud_y_v = hud_main.findViewById(R.id.hud_y);
                hud_f_v = hud_main.findViewById(R.id.hud_f);
                hud_y_v.setVisibility(View.INVISIBLE);
                hud_f_v.setVisibility(View.INVISIBLE);
                TeclasAbertas = false;
                refreshRuntimeChrome();
                schedulePhoneOverlayWarmup();
                scheduleInventoryOverlayWarmup();

                hud_main.findViewById(R.id.WeaponShowLayout).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called weapon wheel");
                        toggleWeaponWheelOverlay();
                    }
                });
                hud_main.findViewById(R.id.btn_weapon_wheel).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called weapon wheel button");
                        toggleWeaponWheelOverlay();
                    }
                });
                hud_main.findViewById(R.id.btn_2).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change RadialVehicles");
                        sendCommandV("/mv".getBytes(StandardCharsets.UTF_8));
                    }
                });
                hud_main.findViewById(R.id.btn_1).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change RadialMenu");
                        mRadialMenu.show();
                    }
                });
                hud_main.findViewById(R.id.btn_0).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change keys");
                        if (TeclasAbertas)
                        {
                            EsconderTeclas();
                        } else if (!TeclasAbertas)
                        {
                            MostrarTeclas();
                        }
                    }
                });
                hud_main.findViewById(R.id.enter_passenger).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change Passager");
                        ClickEnterPassengerButton();
                    }
                });
                hud_main.findViewById(R.id.vehicle_lock_butt).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change Trancar");
                        long currTime = System.currentTimeMillis()/1000;
                        if(buttonLockCD > currTime)
                        {
                            return;
                        }
                        buttonLockCD = currTime+2;
                        ClickLockVehicleButton();
                    }
                });
                hud_main.findViewById(R.id.hud_y).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change y");
                        onClickButton(1);
                        //EsconderTeclas();
                    }
                });
                hud_main.findViewById(R.id.hud_f).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change f");
                        onClickButton(2);
                        //EsconderTeclas();
                    }
                });
                hud_main.findViewById(R.id.hide_chat).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called change ocultar chat");
                        MostrarChat();
                    }
                });
                hud_main.findViewById(R.id.btn_game_settings).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Log.i("LogCat","Called safe HUD settings");
                        openSafeHudSettings();
                    }
                });
            }
        });
    }

    int DialogId = 0;

    public void hidehud()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(hud_main == null) return;
                iShowHud = 0;
                cancelOverlayWarmups();
                hidePhoneOverlayInternal();
                hideInventoryOverlayInternal();
                hideMapOverlayInternal();
                hideWeaponWheelOverlayInternal();
                setOverlayBlurEnabledInternal(false);
                hideHudSettingsPanel();
                hud_main.setVisibility(View.GONE);
                hud1 = hud_main.findViewById(R.id.hud);


                hud1.setVisibility(View.GONE);
                hud1.setAlpha(0.0f);

            }
        });
    }

    public void UpdateHud(int hp, int armour, int eat, int money,int gunId,int ammo)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(hud_main == null) return;

                hpBar = hud_main.findViewById(R.id.progressBarHeart);
                armourBar = hud_main.findViewById(R.id.progressBarArmour);
                eatBar = hud_main.findViewById(R.id.progressBarEat);
                sedeBar = hud_main.findViewById(R.id.progressBarSede);
                sonoBar = hud_main.findViewById(R.id.progressBarSono);
                moneyText = hud_main.findViewById(R.id.money);
                hpText = hud_main.findViewById(R.id.hpText);
                armourText= hud_main.findViewById(R.id.Armourtext);

                setCircularStatusProgress(hpBar, hp);
                setCircularStatusProgress(armourBar, armour);
                setCircularStatusProgress(eatBar, eat);
                setCircularStatusProgress(sedeBar, eat - 10);
                setCircularStatusProgress(sonoBar, 88);

                String moneyStr = String.format(Locale.ITALY, "%,d", money);

                moneyText.setText(moneyStr);

                hpText.setText(Integer.toString(hp) + "%");
                armourText.setText(Integer.toString(armour) + "%");
                
                gunImg = hud_main.findViewById(R.id.gunImg);
                ammoText = hud_main.findViewById(R.id.ammo);

                //Fist Update


                if(gunId == 0)
                {
                    hud_main.findViewById(R.id.WeaponShowLayout).setVisibility(View.VISIBLE);
                    hud_main.findViewById(R.id.Fist).setVisibility(View.VISIBLE);
                }
                else
                {
                    hud_main.findViewById(R.id.WeaponShowLayout).setVisibility(View.VISIBLE);
                    hud_main.findViewById(R.id.Fist).setVisibility(View.GONE);
                }


                int resId = getResources().getIdentifier("weapon_" + gunId, "drawable", getPackageName());
                if (resId != 0) {
                    gunImg.setImageResource(resId);
                }
                ammoText.setText(Integer.toString(ammo));
                if (!receivedNativeWeaponWheelSnapshot) {
                    updateWeaponWheelFallback(gunId, ammo);
                }

            }
        });
    }

    public void UpdateWeaponWheel(String weaponsJson)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receivedNativeWeaponWheelSnapshot = true;
                lastWeaponWheelJson = sanitizeWeaponWheelJson(weaponsJson);
                syncWeaponWheelPayload();
            }
        });
    }
    //API
    private String lerNomeDoPlayer() {
        try {
            File file = new File(getExternalFilesDir(null) + "/SAMP/settings.ini");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("name")) {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            return parts[1].trim();
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Jogador"; // fallback se der erro
    }
    //WEBVIEW
    // Flag para verificar se a pÃ¡gina jÃ¡ foi carregada
    private boolean webcellcarregada = false;
    public void ShowCell() {
        showPhoneOverlay();
    }
    private void HideWebView(WebView WebView) {
        if (WebView.getVisibility() == View.VISIBLE) {
            WebView.setVisibility(View.GONE);
            Log.i("WebView", "WebView ocultada com sucesso.");
        } else {
            Log.i("WebView", "WebView jÃ¡ estava oculta.");
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeRuntimeOverlays() {
        framewebcell = hud_main.findViewById(R.id.framewebcell);
        webcell = hud_main.findViewById(R.id.webcell);
        framewebInventory = hud_main.findViewById(R.id.framewebacess);
        webInventory = hud_main.findViewById(R.id.webacess);
        framewebMiniMap = hud_main.findViewById(R.id.framewebmap);
        webMiniMap = hud_main.findViewById(R.id.webmap);
        framewebWeaponWheel = hud_main.findViewById(R.id.framewebweaponwheel);
        webWeaponWheel = hud_main.findViewById(R.id.webweaponwheel);
        overlayBlurScrim = hud_main.findViewById(R.id.overlay_blur_scrim);
        runtimeOverlayBridge = new RuntimeOverlayBridge();

        if (USE_NATIVE_IMGUI_OVERLAYS) {
            hidePhoneOverlayInternal();
            hideInventoryOverlayInternal();
            hideMapOverlayInternal();
            hideWeaponWheelOverlayInternal();
            setOverlayBlurEnabledInternal(false);
            setNativeOverlayState(NATIVE_OVERLAY_NONE);
            return;
        }

        phoneOverlayConfigured = false;
        inventoryOverlayConfigured = false;
        mapOverlayConfigured = false;
        weaponWheelOverlayConfigured = false;
        hidePhoneOverlayInternal();
        hideInventoryOverlayInternal();
        hideMapOverlayInternal();
        hideWeaponWheelOverlayInternal();
        setOverlayBlurEnabledInternal(false);
        schedulePhoneOverlayWarmup();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureRuntimeOverlay(WebView webView, String assetUrl) {
        if (webView == null) {
            return;
        }

        final String overlayName = PHONE_OVERLAY_URL.equals(assetUrl)
                ? "phone"
                : INVENTORY_OVERLAY_URL.equals(assetUrl)
                ? "inventory"
                : MAP_OVERLAY_URL.equals(assetUrl)
                ? "map"
                : WEAPON_WHEEL_OVERLAY_URL.equals(assetUrl) ? "weaponWheel" : assetUrl;
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(false);
        }

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setInitialScale(100);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
        webView.setOnLongClickListener(v -> true);
        webView.addJavascriptInterface(runtimeOverlayBridge, "Android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (VERBOSE_OVERLAY_LOGS) {
                    Log.d(
                            TAG,
                            "Overlay[" + overlayName + "] "
                                    + consoleMessage.message()
                                    + " @"
                                    + consoleMessage.sourceId()
                                    + ":"
                                    + consoleMessage.lineNumber()
                    );
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (VERBOSE_OVERLAY_LOGS) {
                    Log.d(TAG, "Overlay[" + overlayName + "] loaded " + url);
                }
                syncRuntimeOverlay(view);
                if (view == webWeaponWheel) {
                    syncWeaponWheelPayload();
                }
                if (view == webcell) {
                    webcellcarregada = true;
                }
            }
        });
        webView.loadUrl(assetUrl);
        webView.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void syncRuntimeOverlay(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.post(() -> webView.evaluateJavascript(
                "window.XyronHydrate && window.XyronHydrate();",
                null
        ));
    }

    private String sanitizeWeaponWheelJson(String weaponsJson) {
        if (weaponsJson == null) {
            return "[{\"id\":0,\"ammo\":0,\"current\":true}]";
        }
        String trimmed = weaponsJson.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        return "[{\"id\":0,\"ammo\":0,\"current\":true}]";
    }

    private void updateWeaponWheelFallback(int gunId, int ammo) {
        int safeGunId = gunId >= 0 && gunId <= 46 ? gunId : 0;
        int safeAmmo = Math.max(0, ammo);
        if (safeGunId == 0) {
            lastWeaponWheelJson = "[{\"id\":0,\"ammo\":0,\"current\":true}]";
        } else {
            lastWeaponWheelJson = "[{\"id\":0,\"ammo\":0,\"current\":false},"
                    + "{\"id\":" + safeGunId
                    + ",\"ammo\":" + safeAmmo
                    + ",\"current\":true}]";
        }
        syncWeaponWheelPayload();
    }

    private void syncWeaponWheelPayload() {
        if (!weaponWheelOverlayConfigured || webWeaponWheel == null) {
            return;
        }
        String payload = sanitizeWeaponWheelJson(lastWeaponWheelJson);
        evaluateOverlayJavascript(
                webWeaponWheel,
                "window.NewsRpWeaponWheelSetInventory&&window.NewsRpWeaponWheelSetInventory("
                        + JSONObject.quote(payload)
                        + ");"
        );
    }

    public void showPhoneOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            runOnUiThread(() -> {
                hideHudSettingsPanel();
                hideInventoryOverlayInternal();
                hideMapOverlayInternal();
                hideWeaponWheelOverlayInternal();
                setNativeOverlayState(NATIVE_OVERLAY_PHONE);
                refreshRuntimeChrome();
            });
            return;
        }
        runOnUiThread(() -> {
            WebView overlay = ensureRuntimeOverlayConfigured("phone");
            if (framewebcell == null || overlay == null) {
                return;
            }
            hideHudSettingsPanel();
            hideInventoryOverlayInternal();
            hideMapOverlayInternal();
            hideWeaponWheelOverlayInternal();
            framewebcell.bringToFront();
            framewebcell.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            updateRuntimeOverlayExecutionState();
            refreshRuntimeChrome();
        });
    }

    public void hidePhoneOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            runOnUiThread(() -> {
                if (getNativeOverlayState() == NATIVE_OVERLAY_PHONE) {
                    setNativeOverlayState(NATIVE_OVERLAY_NONE);
                }
                refreshRuntimeChrome();
            });
            return;
        }
        runOnUiThread(this::hidePhoneOverlayInternal);
    }

    public void showInventoryOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            runOnUiThread(() -> {
                hideHudSettingsPanel();
                hidePhoneOverlayInternal();
                hideMapOverlayInternal();
                hideWeaponWheelOverlayInternal();
                setNativeOverlayState(NATIVE_OVERLAY_INVENTORY);
                refreshRuntimeChrome();
            });
            return;
        }
        runOnUiThread(() -> {
            WebView overlay = ensureRuntimeOverlayConfigured("inventory");
            if (framewebInventory == null || overlay == null) {
                return;
            }
            hideHudSettingsPanel();
            hidePhoneOverlayInternal();
            hideMapOverlayInternal();
            hideWeaponWheelOverlayInternal();
            framewebInventory.bringToFront();
            framewebInventory.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            updateRuntimeOverlayExecutionState();
            refreshRuntimeChrome();
        });
    }

    public void hideInventoryOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            runOnUiThread(() -> {
                if (getNativeOverlayState() == NATIVE_OVERLAY_INVENTORY) {
                    setNativeOverlayState(NATIVE_OVERLAY_NONE);
                }
                refreshRuntimeChrome();
            });
            return;
        }
        runOnUiThread(this::hideInventoryOverlayInternal);
    }

    public void toggleWeaponWheelOverlay() {
        if (isWeaponWheelOverlayVisible()) {
            hideWeaponWheelOverlay();
        } else {
            showWeaponWheelOverlay();
        }
    }

    public void showWeaponWheelOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            return;
        }
        runOnUiThread(() -> {
            WebView overlay = ensureRuntimeOverlayConfigured("weaponWheel");
            if (framewebWeaponWheel == null || overlay == null) {
                Toast.makeText(this, "Roleta de armas indisponivel.", Toast.LENGTH_SHORT).show();
                return;
            }
            hideHudSettingsPanel();
            hidePhoneOverlayInternal();
            hideInventoryOverlayInternal();
            hideMapOverlayInternal();
            if (RadialMenu.menuVisible && mRadialMenu != null) {
                mRadialMenu.hide();
            }
            if (Radinho.radinhoVisible && mRadinho != null) {
                mRadinho.hide();
            }
            framewebWeaponWheel.bringToFront();
            framewebWeaponWheel.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            syncWeaponWheelPayload();
            updateRuntimeOverlayExecutionState();
            refreshRuntimeChrome();
            hideSystemUI();
        });
    }

    public void hideWeaponWheelOverlay() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            return;
        }
        runOnUiThread(this::hideWeaponWheelOverlayInternal);
    }

    private void hidePhoneOverlayInternal() {
        setPhoneOverlayHiddenState(phoneOverlayConfigured ? View.INVISIBLE : View.GONE);
        updateRuntimeOverlayExecutionState();
        refreshRuntimeChrome();
    }

    private void hideInventoryOverlayInternal() {
        setInventoryOverlayHiddenState(inventoryOverlayConfigured ? View.INVISIBLE : View.GONE);
        updateRuntimeOverlayExecutionState();
        refreshRuntimeChrome();
    }

    private void hideWeaponWheelOverlayInternal() {
        setWeaponWheelOverlayHiddenState(weaponWheelOverlayConfigured ? View.INVISIBLE : View.GONE);
        updateRuntimeOverlayExecutionState();
        refreshRuntimeChrome();
    }

    private boolean isPhoneOverlayVisible() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            return getNativeOverlayState() == NATIVE_OVERLAY_PHONE;
        }
        return framewebcell != null && framewebcell.getVisibility() == View.VISIBLE;
    }

    private boolean isInventoryOverlayVisible() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            return getNativeOverlayState() == NATIVE_OVERLAY_INVENTORY;
        }
        return framewebInventory != null && framewebInventory.getVisibility() == View.VISIBLE;
    }

    private boolean isWeaponWheelOverlayVisible() {
        return !USE_NATIVE_IMGUI_OVERLAYS
                && framewebWeaponWheel != null
                && framewebWeaponWheel.getVisibility() == View.VISIBLE;
    }

    private boolean isPickupCreatorVisible() {
        return mPickupCreatorOverlay != null && mPickupCreatorOverlay.isVisible();
    }

    private boolean handleRuntimeBack() {
        if (isHudSettingsVisible()) {
            hideHudSettingsPanel();
            return true;
        }
        if (isPickupCreatorVisible()) {
            mPickupCreatorOverlay.hide();
            return true;
        }
        if (isPhoneOverlayVisible()) {
            hidePhoneOverlay();
            return true;
        }
        if (isInventoryOverlayVisible()) {
            hideInventoryOverlay();
            return true;
        }
        if (isWeaponWheelOverlayVisible()) {
            hideWeaponWheelOverlay();
            return true;
        }
        if (RadialMenu.menuVisible) {
            mRadialMenu.hide();
            return true;
        }
        if (Radinho.radinhoVisible) {
            mRadinho.hide();
            return true;
        }
        return false;
    }

    private void destroyRuntimeOverlays() {
        if (USE_NATIVE_IMGUI_OVERLAYS) {
            setNativeOverlayState(NATIVE_OVERLAY_NONE);
            return;
        }
        destroyWebView(webcell);
        destroyWebView(webInventory);
        destroyWebView(webMiniMap);
        destroyWebView(webWeaponWheel);
    }

    private void destroyWebView(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.removeJavascriptInterface("Android");
        webView.loadUrl("about:blank");
        webView.stopLoading();
        webView.destroy();
    }

    private void dispatchRuntimeCommand(String command) {
        if (command == null) {
            return;
        }
        String sanitized = command.trim();
        if (sanitized.isEmpty()) {
            return;
        }
        if (handleLocalRuntimeCommand(sanitized)) {
            return;
        }
        sendCommandV(sanitized.getBytes(StandardCharsets.UTF_8));
    }

    private boolean handleLocalRuntimeCommand(String command) {
        if (command == null) {
            return false;
        }
        String sanitized = command.trim();
        if (sanitized.isEmpty()) {
            return false;
        }

        String normalized = sanitized.toLowerCase(Locale.US);
        if ("/criarpickup".equals(normalized)
                || normalized.startsWith("/criarpickup ")
                || "/pickupstudio".equals(normalized)) {
            runOnUiThread(this::openPickupCreatorOverlay);
            return true;
        }
        return false;
    }

    private void openPickupCreatorOverlay() {
        hideKeyboard();
        hidePhoneOverlay();
        hideInventoryOverlay();
        hideWeaponWheelOverlay();
        hideMapOverlayInternal();
        hideSystemUI();
        if (mPickupCreatorOverlay != null) {
            mPickupCreatorOverlay.show();
        }
    }

    private String getSelectedServerName() {
        ServerConfigManager.ServerOption option = ServerConfigManager.getSelectedServer(this);
        return option.name;
    }

    private String getSelectedServerAddress() {
        ServerConfigManager.ServerOption option = ServerConfigManager.getSelectedServer(this);
        return option.getAddress();
    }

    private void showMapOverlayInternal() {
        hideMapOverlayInternal();
    }

    private void hideMapOverlayInternal() {
        if (framewebMiniMap != null) {
            framewebMiniMap.setVisibility(View.GONE);
        }
        if (webMiniMap != null) {
            webMiniMap.setVisibility(View.GONE);
        }
        updateRuntimeOverlayExecutionState();
    }

    private void refreshRuntimeChrome() {
        boolean modalVisible = isPhoneOverlayVisible()
                || isInventoryOverlayVisible()
                || isWeaponWheelOverlayVisible()
                || isPickupCreatorVisible()
                || isHudSettingsVisible();
        setOverlayBlurEnabledInternal(modalVisible);
        if (modalVisible) {
            hideMapOverlayInternal();
        } else {
            showMapOverlayInternal();
        }
    }

    private void openNativePauseMenu() {
        try {
            setAllowNextNativePauseMenu(true);
            pauseEvent();
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Nao foi possivel abrir o menu nativo.", error);
        }
    }

    private void requestNativeGameplayResume() {
        try {
            setAllowNextNativePauseMenu(false);
            forceEndNativeUserPause();
            resumeEvent();
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Nao foi possivel retomar o gameplay nativo.", error);
        }
        hideSystemUI();
    }

    private void openSafeHudSettings() {
        if (isHudSettingsVisible()) {
            hideHudSettingsPanel();
            return;
        }
        showHudSettingsPanel();
    }

    private void openNativeGameSettings() {
        openNativePauseMenu();
        uiHandler.postDelayed(
                () -> performNativeMenuTap(
                        NATIVE_MENU_SETTINGS_X_RATIO,
                        NATIVE_MENU_SETTINGS_Y_RATIO
                ),
                NATIVE_SETTINGS_FIRST_TAP_DELAY_MS
        );
        uiHandler.postDelayed(
                () -> performNativeMenuTap(
                        NATIVE_MENU_SETTINGS_X_RATIO,
                        NATIVE_MENU_SETTINGS_Y_RATIO
                ),
                NATIVE_SETTINGS_SECOND_TAP_DELAY_MS
        );
    }

    private void performNativeMenuTap(float xRatio, float yRatio) {
        View decorView = getWindow() != null ? getWindow().getDecorView() : null;
        if (decorView == null) {
            Log.w(TAG, "performNativeMenuTap ignored: decorView null");
            return;
        }

        int width = decorView.getWidth();
        int height = decorView.getHeight();
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "performNativeMenuTap ignored: invalid bounds " + width + "x" + height);
            return;
        }

        float x = width * xRatio;
        float y = height * yRatio;
        if (injectTouchWithInputManager(x, y)) {
            return;
        }
        try {
            sendSyntheticNativeTouch(Math.round(x), Math.round(y));
            return;
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Falha ao enviar toque nativo sintetico.", error);
        }
        Log.i(TAG, "performNativeMenuTap x=" + x + " y=" + y + " size=" + width + "x" + height);
        Thread injectorThread = new Thread(() -> {
            long downTime = SystemClock.uptimeMillis();
            MotionEvent downEvent = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    0
            );
            MotionEvent upEvent = MotionEvent.obtain(
                    downTime,
                    downTime + 24L,
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    0
            );
            downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

            try {
                Instrumentation instrumentation = new Instrumentation();
                instrumentation.sendPointerSync(downEvent);
                instrumentation.sendPointerSync(upEvent);
            } catch (RuntimeException error) {
                Log.e(TAG, "Falha ao injetar toque nativo.", error);
            } finally {
                downEvent.recycle();
                upEvent.recycle();
            }
        }, "xyron-native-menu-tap");
        injectorThread.start();
    }

    private boolean injectTouchWithInputManager(float x, float y) {
        try {
            InputManager inputManager = (InputManager) getSystemService(INPUT_SERVICE);
            if (inputManager == null) {
                return false;
            }

            Method injectMethod = InputManager.class.getMethod(
                    "injectInputEvent",
                    android.view.InputEvent.class,
                    int.class
            );
            injectMethod.setAccessible(true);

            long downTime = SystemClock.uptimeMillis();
            MotionEvent downEvent = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    0
            );
            MotionEvent upEvent = MotionEvent.obtain(
                    downTime,
                    downTime + 24L,
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    0
            );
            downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

            boolean injectedDown;
            boolean injectedUp;
            try {
                injectedDown = (boolean) injectMethod.invoke(inputManager, downEvent, 0);
                injectedUp = (boolean) injectMethod.invoke(inputManager, upEvent, 0);
            } finally {
                downEvent.recycle();
                upEvent.recycle();
            }

            return injectedDown && injectedUp;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldIgnoreTransientFocusLoss() {
        return !isFinishing()
                && !isChangingConfigurations()
                && !isPickupCreatorVisible()
                && !isPhoneOverlayVisible()
                && !isInventoryOverlayVisible()
                && !RadialMenu.menuVisible
                && !Radinho.radinhoVisible;
    }

    private boolean shouldHandleSystemUiMapMenu() {
        return !isFinishing()
                && !isChangingConfigurations()
                && !isPickupCreatorVisible()
                && !isPhoneOverlayVisible()
                && !isInventoryOverlayVisible()
                && !RadialMenu.menuVisible
                && !Radinho.radinhoVisible;
    }

    private boolean shouldSuppressAutomaticPauseMenu() {
        return shouldHandleSystemUiMapMenu();
    }

    private boolean shouldAutoDismissNativeMenu() {
        return !isFinishing() && !isChangingConfigurations();
    }

    @Override
    protected boolean shouldDispatchAutomaticPauseResume() {
        return !suppressAutomaticNativePauseResume;
    }

    private void scheduleAutomaticPauseResumeSuppressionRelease() {
        uiHandler.removeCallbacks(clearAutomaticPauseResumeSuppressionRunnable);
        uiHandler.postDelayed(
                clearAutomaticPauseResumeSuppressionRunnable,
                AUTOMATIC_PAUSE_RESUME_SUPPRESSION_RELEASE_DELAY_MS
        );
    }

    private void scheduleBackgroundMenuDismissSequence() {
        if (!pendingBackgroundMenuDismiss) {
            Log.i(TAG, "scheduleBackgroundMenuDismissSequence skipped: pending=false");
            return;
        }
        uiHandler.removeCallbacks(dismissMenuAfterBackgroundRunnable);
        backgroundMenuDismissStep = 0;
        Log.i(TAG, "scheduleBackgroundMenuDismissSequence start");
        uiHandler.postDelayed(
                dismissMenuAfterBackgroundRunnable,
                NATIVE_MENU_RESUME_AFTER_BACKGROUND_DELAYS_MS[0]
        );
    }

    private void setOverlayBlurEnabledInternal(boolean enabled) {
        if (overlayBlurScrim != null) {
            overlayBlurScrim.setVisibility(enabled ? View.VISIBLE : View.GONE);
            overlayBlurScrim.setAlpha(enabled ? 1.0f : 0.0f);
        }

        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) {
            return;
        }

        for (int i = 0; i < contentRoot.getChildCount(); i += 1) {
            View child = contentRoot.getChildAt(i);
            if (child == null || child == hud_main) {
                continue;
            }
            child.setAlpha(enabled ? 0.88f : 1.0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                child.setRenderEffect(enabled && !runtimeLowRamMode
                        ? RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
                        : null);
            }
        }
    }

    private String extractHostFromAddress(String address) {
        if (address == null) {
            return "";
        }
        String value = address.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.contains("://")) {
            try {
                return new java.net.URI(value).getHost();
            } catch (Exception error) {
                return "";
            }
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex > 0) {
            return value.substring(0, colonIndex);
        }
        return value;
    }

    private String buildRuntimeConfigJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("playerName", lerNomeDoPlayer());
            root.put("serverName", getSelectedServerName());
            root.put("serverAddress", getSelectedServerAddress());

            JSONObject live = new JSONObject();
            live.put("enabled", false);
            live.put("port", 8766);
            live.put("path", "/live");
            live.put("reconnectMs", 1800);
            live.put("previewUrl", "ws://127.0.0.1:8766/live");

            String host = extractHostFromAddress(getSelectedServerAddress());
            if (!host.isEmpty()) {
                live.put("runtimeUrl", "ws://" + host + ":8766/live");
            }

            JSONObject cdn = new JSONObject();
            cdn.put("enabled", true);
            cdn.put("baseUrl", "https://raw.githubusercontent.com/qbcore-framework/qb-inventory/main/html");

            JSONObject performance = new JSONObject();
            performance.put("lowRam", runtimeLowRamMode);

            root.put("liveReload", live);
            root.put("cdn", cdn);
            root.put("performance", performance);
        } catch (JSONException error) {
            Log.e(TAG, "Runtime config JSON failed", error);
        }
        return root.toString();
    }

    private boolean detectLowRamMode() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice()) {
            return true;
        }
        return activityManager.getMemoryClass() <= 192;
    }

    private void setPhoneOverlayHiddenState(int visibility) {
        if (framewebcell != null) {
            framewebcell.setVisibility(visibility);
        }
        if (webcell != null) {
            webcell.setVisibility(visibility);
        }
    }

    private void setInventoryOverlayHiddenState(int visibility) {
        if (framewebInventory != null) {
            framewebInventory.setVisibility(visibility);
        }
        if (webInventory != null) {
            webInventory.setVisibility(visibility);
        }
    }

    private void setWeaponWheelOverlayHiddenState(int visibility) {
        if (framewebWeaponWheel != null) {
            framewebWeaponWheel.setVisibility(visibility);
        }
        if (webWeaponWheel != null) {
            webWeaponWheel.setVisibility(visibility);
        }
    }

    private void schedulePhoneOverlayWarmup() {
        if (USE_NATIVE_IMGUI_OVERLAYS || phoneOverlayConfigured || handler == null) {
            return;
        }

        if (phoneOverlayWarmupRunnable == null) {
            phoneOverlayWarmupRunnable = () -> {
                if (phoneOverlayConfigured) {
                    return;
                }
                WebView overlay = ensureRuntimeOverlayConfigured("phone");
                if (overlay == null) {
                    return;
                }
                syncRuntimeOverlay(overlay);
                setPhoneOverlayHiddenState(View.INVISIBLE);
                updateRuntimeOverlayExecutionState();
            };
        }

        handler.removeCallbacks(phoneOverlayWarmupRunnable);
        handler.postDelayed(
                phoneOverlayWarmupRunnable,
                runtimeLowRamMode ? PHONE_OVERLAY_WARMUP_LOW_RAM_DELAY_MS : PHONE_OVERLAY_WARMUP_DELAY_MS
        );
    }

    private void scheduleInventoryOverlayWarmup() {
        if (USE_NATIVE_IMGUI_OVERLAYS || inventoryOverlayConfigured || handler == null) {
            return;
        }

        if (inventoryOverlayWarmupRunnable == null) {
            inventoryOverlayWarmupRunnable = () -> {
                if (inventoryOverlayConfigured || iShowHud != 1) {
                    return;
                }
                WebView overlay = ensureRuntimeOverlayConfigured("inventory");
                if (overlay == null) {
                    return;
                }
                syncRuntimeOverlay(overlay);
                setInventoryOverlayHiddenState(View.INVISIBLE);
                updateRuntimeOverlayExecutionState();
            };
        }

        handler.removeCallbacks(inventoryOverlayWarmupRunnable);
        handler.postDelayed(
                inventoryOverlayWarmupRunnable,
                runtimeLowRamMode ? INVENTORY_OVERLAY_LOW_RAM_DELAY_MS : INVENTORY_OVERLAY_WARMUP_DELAY_MS
        );
    }

    private void cancelOverlayWarmups() {
        if (handler == null) {
            return;
        }
        if (phoneOverlayWarmupRunnable != null) {
            handler.removeCallbacks(phoneOverlayWarmupRunnable);
        }
        if (inventoryOverlayWarmupRunnable != null) {
            handler.removeCallbacks(inventoryOverlayWarmupRunnable);
        }
    }

    private WebView ensureRuntimeOverlayConfigured(String overlayName) {
        if ("phone".equalsIgnoreCase(overlayName)) {
            if (!phoneOverlayConfigured && webcell != null) {
                configureRuntimeOverlay(webcell, PHONE_OVERLAY_URL);
                phoneOverlayConfigured = true;
            }
            return webcell;
        }

        if ("inventory".equalsIgnoreCase(overlayName)) {
            if (!inventoryOverlayConfigured && webInventory != null) {
                configureRuntimeOverlay(webInventory, INVENTORY_OVERLAY_URL);
                inventoryOverlayConfigured = true;
            }
            return webInventory;
        }

        if ("map".equalsIgnoreCase(overlayName)) {
            if (!mapOverlayConfigured && webMiniMap != null) {
                configureRuntimeOverlay(webMiniMap, MAP_OVERLAY_URL);
                mapOverlayConfigured = true;
            }
            return webMiniMap;
        }

        if ("weaponWheel".equalsIgnoreCase(overlayName)) {
            if (!weaponWheelOverlayConfigured && webWeaponWheel != null) {
                configureRuntimeOverlay(webWeaponWheel, WEAPON_WHEEL_OVERLAY_URL);
                weaponWheelOverlayConfigured = true;
            }
            return webWeaponWheel;
        }

        return null;
    }

    private void setRuntimeOverlayActive(WebView webView, boolean active) {
        if (webView == null) {
            return;
        }

        boolean keepHot = !active && shouldKeepOverlayHot(webView);
        evaluateOverlayJavascript(
                webView,
                "(function(d){if(!d||!d.documentElement){return;}d.documentElement.dataset.xyronVisible="
                        + (active ? "'1'" : "'0'")
                        + ";window.XyronOverlayVisibilityChanged&&window.XyronOverlayVisibilityChanged("
                        + (active ? "true" : "false")
                        + ");})(document);"
        );

        if (active || keepHot) {
            webView.onResume();
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            webView.onPause();
            webView.clearFocus();
            webView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private boolean shouldKeepOverlayHot(WebView webView) {
        return !runtimeLowRamMode && webView == webcell && phoneOverlayConfigured && iShowHud == 1;
    }

    private void updateRuntimeOverlayExecutionState() {
        if (phoneOverlayConfigured) {
            setRuntimeOverlayActive(
                    webcell,
                    framewebcell != null
                            && framewebcell.getVisibility() == View.VISIBLE
                            && webcell != null
                            && webcell.getVisibility() == View.VISIBLE
            );
        }

        if (inventoryOverlayConfigured) {
            setRuntimeOverlayActive(
                    webInventory,
                    framewebInventory != null
                            && framewebInventory.getVisibility() == View.VISIBLE
                            && webInventory != null
                            && webInventory.getVisibility() == View.VISIBLE
            );
        }

        if (mapOverlayConfigured) {
            setRuntimeOverlayActive(
                    webMiniMap,
                    framewebMiniMap != null
                            && framewebMiniMap.getVisibility() == View.VISIBLE
                            && webMiniMap != null
                            && webMiniMap.getVisibility() == View.VISIBLE
            );
        }

        if (weaponWheelOverlayConfigured) {
            setRuntimeOverlayActive(
                    webWeaponWheel,
                    framewebWeaponWheel != null
                            && framewebWeaponWheel.getVisibility() == View.VISIBLE
                            && webWeaponWheel != null
                            && webWeaponWheel.getVisibility() == View.VISIBLE
            );
        }
    }

    private WebView resolveOverlayWebView(String overlayName) {
        if ("inventory".equalsIgnoreCase(overlayName)) {
            return ensureRuntimeOverlayConfigured("inventory");
        }
        if ("weaponWheel".equalsIgnoreCase(overlayName)) {
            return ensureRuntimeOverlayConfigured("weaponWheel");
        }
        if ("map".equalsIgnoreCase(overlayName)) {
            return null;
        }
        if ("phone".equalsIgnoreCase(overlayName)) {
            return ensureRuntimeOverlayConfigured("phone");
        }
        if (isPhoneOverlayVisible()) {
            return webcell;
        }
        if (isInventoryOverlayVisible()) {
            return webInventory;
        }
        if (isWeaponWheelOverlayVisible()) {
            return webWeaponWheel;
        }
        return webcell;
    }

    private void evaluateOverlayJavascript(WebView webView, String script) {
        if (webView == null || script == null || script.isEmpty()) {
            return;
        }
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void dispatchRuntimeCallback(WebView webView, String callbackName, JSONObject payload) {
        if (webView == null || callbackName == null || callbackName.isEmpty()) {
            return;
        }
        JSONObject safePayload = payload != null ? payload : new JSONObject();
        String raw = safePayload.toString();
        evaluateOverlayJavascript(
                webView,
                callbackName + " && " + callbackName + "(" + JSONObject.quote(raw) + ");"
        );
    }

    private boolean launchVoiceCommand(String overlayName) {
        WebView target = resolveOverlayWebView(overlayName);
        pendingVoiceTarget = target;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("status", "error");
                payload.put("error", "permission");
            } catch (JSONException ignored) {
            }
            dispatchRuntimeCallback(target, "window.XyronRuntimeReceiveVoice", payload);
            Toast.makeText(this, "Permissao de microfone nao concedida.", Toast.LENGTH_SHORT).show();
            return false;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale um comando do servidor");

        if (intent.resolveActivity(getPackageManager()) == null) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("status", "error");
                payload.put("error", "recognizer_unavailable");
            } catch (JSONException ignored) {
            }
            dispatchRuntimeCallback(target, "window.XyronRuntimeReceiveVoice", payload);
            Toast.makeText(this, "Reconhecimento de voz indisponivel.", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            startActivityForResult(intent, VOICE_COMMAND_REQUEST);
            return true;
        } catch (Exception error) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("status", "error");
                payload.put("error", "launch_failed");
            } catch (JSONException ignored) {
            }
            dispatchRuntimeCallback(target, "window.XyronRuntimeReceiveVoice", payload);
            Log.e(TAG, "Voice command launch failed", error);
            return false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_COMMAND_REQUEST) {
            WebView target = pendingVoiceTarget != null ? pendingVoiceTarget : resolveOverlayWebView("phone");
            pendingVoiceTarget = null;
            JSONObject payload = new JSONObject();
            try {
                if (resultCode != RESULT_OK) {
                    payload.put("status", "cancelled");
                } else {
                    ArrayList<String> results = data != null
                            ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            : null;
                    String transcript = (results != null && !results.isEmpty()) ? results.get(0) : "";
                    if (transcript.isEmpty()) {
                        payload.put("status", "error");
                        payload.put("error", "empty");
                    } else {
                        payload.put("status", "ok");
                        payload.put("transcript", transcript);
                    }
                }
            } catch (JSONException error) {
                Log.e(TAG, "Voice result JSON failed", error);
            }

            dispatchRuntimeCallback(target, "window.XyronRuntimeReceiveVoice", payload);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private final class RuntimeOverlayBridge {
        @JavascriptInterface
        public void closePhone() {
            hidePhoneOverlay();
        }

        @JavascriptInterface
        public void closeInventory() {
            hideInventoryOverlay();
        }

        @JavascriptInterface
        public void closeWeaponWheel() {
            hideWeaponWheelOverlay();
        }

        @JavascriptInterface
        public void openPhone() {
            showPhoneOverlay();
        }

        @JavascriptInterface
        public void openInventory() {
            showInventoryOverlay();
        }

        @JavascriptInterface
        public void openWeaponWheel() {
            showWeaponWheelOverlay();
        }

        @JavascriptInterface
        public void selectWeapon(int weaponId) {
            runOnUiThread(() -> {
                int safeWeaponId = weaponId >= 0 && weaponId <= 46 ? weaponId : 0;
                try {
                    SAMP.this.selectWeapon(safeWeaponId);
                } catch (UnsatisfiedLinkError error) {
                    Log.e(TAG, "Native selectWeapon indisponivel.", error);
                }
                hideWeaponWheelOverlayInternal();
            });
        }

        @JavascriptInterface
        public void runCommand(String command) {
            runOnUiThread(() -> dispatchRuntimeCommand(command));
        }

        @JavascriptInterface
        public String getPlayerName() {
            return lerNomeDoPlayer();
        }

        @JavascriptInterface
        public String getServerName() {
            return getSelectedServerName();
        }

        @JavascriptInterface
        public String getServerAddress() {
            return getSelectedServerAddress();
        }

        @JavascriptInterface
        public String getRuntimeConfig() {
            return buildRuntimeConfigJson();
        }

        @JavascriptInterface
        public void setOverlayBlurEnabled(boolean enabled) {
            runOnUiThread(() -> setOverlayBlurEnabledInternal(enabled));
        }

        @JavascriptInterface
        public boolean startVoiceCommand(String overlayName) {
            final boolean[] result = { false };
            CountDownLatch latch = new CountDownLatch(1);
            runOnUiThread(() -> {
                result[0] = launchVoiceCommand(overlayName);
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return result[0];
        }

        @JavascriptInterface
        public void mensagem() {
            runOnUiThread(() -> Toast.makeText(
                    SAMP.this,
                    "Bridge ativa no runtime.",
                    Toast.LENGTH_SHORT
            ).show());
        }
    }

    /*@JavascriptInterface
    public void fecharWebAcessJS() {
        runOnUiThread(() -> {
            framewebacess.setVisibility(View.GONE);
            webacess.setVisibility(View.GONE);
        });
    }*/
}
