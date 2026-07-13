package com.xyron.game.launcher;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.xyron.game.R;
import com.xyron.game.BuildConfig;
import com.xyron.game.launcher.data.Config;
import com.xyron.game.launcher.fragments.CreditsFragment;
import com.xyron.game.launcher.fragments.DonateFragment;
import com.xyron.game.launcher.fragments.EditorFragment;
import com.xyron.game.launcher.fragments.HomeFragment;
import com.xyron.game.launcher.fragments.HostFragment;
import com.xyron.game.launcher.fragments.HostFilesFragment;
import com.xyron.game.launcher.fragments.ServersFragment;
import com.xyron.game.launcher.fragments.SettingsFragment;
import com.xyron.game.launcher.fragments.WorkbenchFragment;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.ConfigValidator;
import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.HostShellEngine;
import com.xyron.game.launcher.util.SampQueryApi;
import com.xyron.game.launcher.util.SAMPServerInfo;
import com.xyron.game.launcher.util.ServerConfigManager;
import com.xyron.game.launcher.util.ViewPagerWithoutSwipe;
import com.xyron.game.main.SAMP;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends SampActivity {
    public static final int TAB_HOME = 0;
    public static final int TAB_SERVERS = 1;
    public static final int TAB_HOST = 2;
    public static final int TAB_HOST_FILES = 3;
    public static final int TAB_EDITOR = 4;
    public static final int TAB_WORKBENCH = 5;
    public static final int TAB_STORE = 6;
    public static final int TAB_SETTINGS = 7;
    public static final int TAB_CREDITS = 8;

    public int[] tabImages = {
            R.drawable.ic_home_off,
            R.drawable.ic_servers_off,
            R.drawable.ic_host_off,
            R.drawable.ic_files_off,
            R.drawable.ic_editor_off,
            R.drawable.ic_tools_off,
            R.drawable.ic_shop_off,
            R.drawable.ic_settings_off,
            R.drawable.ic_credits_off
    };

    public int[] tabSelectedImages = {
            R.drawable.ic_home_on,
            R.drawable.ic_servers_on,
            R.drawable.ic_host_on,
            R.drawable.ic_files_on,
            R.drawable.ic_editor_on,
            R.drawable.ic_tools_on,
            R.drawable.ic_shop_on,
            R.drawable.ic_settings_on,
            R.drawable.ic_credits_on
    };

    public static ArrayList<SAMPServerInfo> mServersFavouriteList = new ArrayList<>();

    boolean bAdsInitialized = false;
    int online;
    public int theme;
    private ViewPagerWithoutSwipe pager;
    private volatile boolean launchInProgress;
    private int[] activeTabs;

    public int getOnline() {
        return online;
    }

    public void setOnline(int online) {
        this.online = online;
    }

    public void startGta() {
        if (launchInProgress) {
            return;
        }

        launchInProgress = true;
        setLaunchControlsEnabled(false);

        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            LaunchPreparation preparation = prepareGameLaunch();
            runOnUiThread(() -> handleLaunchPreparation(preparation));
        }, "xyron-launch-prep").start();
    }

    private LaunchPreparation prepareGameLaunch() {
        if (!GameDataVerifier.hasRequiredGameData(getApplicationContext())) {
            return LaunchPreparation.updateRequired();
        }

        List<ServerConfigManager.ServerOption> availableServers =
                ServerConfigManager.getAvailableServers(getApplicationContext());
        if (availableServers.isEmpty()) {
            return LaunchPreparation.blocked(
                    "Adicione um servidor em Configuracoes antes de abrir o jogo.",
                    TAB_SETTINGS
            );
        }

        ServerConfigManager.ensureSelectedServer(getApplicationContext());
        ServerConfigManager.ServerOption selectedServer =
                ServerConfigManager.getSelectedServer(getApplicationContext());
        if (selectedServer == null || !selectedServer.isValid()) {
            return LaunchPreparation.blocked(
                    "Selecione um servidor valido antes de jogar.",
                    TAB_SERVERS
            );
        }

        boolean localHostSelected = isLocalHostOption(selectedServer);
        if (shouldAllowImmediateLocalLaunch(selectedServer)) {
            return LaunchPreparation.ready();
        }

        boolean selectedOnline = false;
        String localHostFailureMessage = null;

        if (localHostSelected) {
            if (!HostShellEngine.isHostReady(getApplicationContext())
                    && !HostShellEngine.isHostRunning(getApplicationContext())
                    && !HostShellEngine.isHostStarting(getApplicationContext())) {
                HostShellEngine.CommandResult bootResult =
                        HostShellEngine.bootHost(getApplicationContext());
                if (bootResult == null || !bootResult.success) {
                    localHostFailureMessage = extractHostFailureMessage(bootResult);
                }
            }

            selectedOnline = HostShellEngine.isHostReady(getApplicationContext())
                    || HostShellEngine.isHostRunning(getApplicationContext())
                    || HostShellEngine.isHostStarting(getApplicationContext());
        }

        if (!selectedOnline) {
            selectedOnline = isServerReachable(selectedServer);
        }

        if (selectedOnline) {
            return LaunchPreparation.ready();
        }

        return LaunchPreparation.blocked(
                localHostSelected && localHostFailureMessage != null
                        ? localHostFailureMessage
                        : "O servidor selecionado nao respondeu agora. Edite ou escolha outro no launcher.",
                -1
        );
    }

    private void handleLaunchPreparation(LaunchPreparation preparation) {
        if (preparation == null) {
            launchInProgress = false;
            setLaunchControlsEnabled(true);
            return;
        }

        if (preparation.requiresDataUpdate) {
            Intent intent = new Intent(this, UpdateActivity.class);
            intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(intent);
            finish();
            return;
        }

        if (preparation.launchGame) {
            launchGameActivity();
            return;
        }

        launchInProgress = false;
        setLaunchControlsEnabled(true);
        if (preparation.message != null && !preparation.message.trim().isEmpty()) {
            Toast.makeText(this, preparation.message, Toast.LENGTH_LONG).show();
        }
        if (preparation.tabToOpen >= 0) {
            openTab(preparation.tabToOpen);
        }
    }

    private void setLaunchControlsEnabled(boolean enabled) {
        View playButton = findViewById(R.id.play_button);
        if (playButton != null) {
            playButton.setEnabled(enabled);
            playButton.setAlpha(enabled ? 1.0f : 0.72f);
        }
    }

    private void launchGameActivity() {
        Intent intent = new Intent(this, SAMP.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void launchInstalledGameApk() {
        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(this);
        if (selectedServer == null || !selectedServer.isValid()) {
            Toast.makeText(this, "Adicione e selecione um servidor antes de abrir o game.", Toast.LENGTH_LONG).show();
            openTab(TAB_SETTINGS);
            return;
        }

        Intent intent = new Intent();
        intent.setClassName(BuildConfig.XYRON_GAME_PACKAGE, "com.xyron.game.launcher.EntryActivity");
        intent.putExtra(SAMP.EXTRA_SERVER_IP, selectedServer.host);
        intent.putExtra(SAMP.EXTRA_SERVER_PORT, selectedServer.port);

        String nickname = readConfiguredNickname();
        if (!nickname.isEmpty()) {
            intent.putExtra(SAMP.EXTRA_NICKNAME, nickname);
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Instale o APK News RP para entrar no servidor.", Toast.LENGTH_LONG).show();
        }
    }

    private String readConfiguredNickname() {
        if (getExternalFilesDir(null) == null) {
            return "";
        }

        File settingsFile = new File(getExternalFilesDir(null), "SAMP/settings.ini");
        if (!settingsFile.exists()) {
            return "";
        }

        try {
            Wini wini = new Wini(settingsFile);
            String nickname = wini.get("client", "name");
            return nickname == null ? "" : nickname.trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean isServerReachable(ServerConfigManager.ServerOption option) {
        if (option == null || !option.isValid()) {
            return false;
        }
        SampQueryApi queryApi = new SampQueryApi(option.host, option.port, 500);
        try {
            return queryApi.isOnline();
        } finally {
            queryApi.close();
        }
    }

    private boolean shouldAllowImmediateLocalLaunch(ServerConfigManager.ServerOption option) {
        if (option == null || !option.isValid()) {
            return false;
        }

        if (!isLocalHostOption(option)) {
            return false;
        }

        return HostShellEngine.isHostReady(getApplicationContext())
                || HostShellEngine.isHostRunning(getApplicationContext())
                || HostShellEngine.isHostStarting(getApplicationContext());
    }

    private boolean isLocalHostOption(ServerConfigManager.ServerOption option) {
        if (option == null || !option.isValid()) {
            return false;
        }
        boolean localLoopback = "127.0.0.1".equals(option.host) || "localhost".equalsIgnoreCase(option.host);
        return localLoopback && option.port == 7777;
    }

    private String extractHostFailureMessage(HostShellEngine.CommandResult result) {
        if (result == null || result.output == null || result.output.trim().isEmpty()) {
            return "O host local nao respondeu agora. Abra a aba Host para revisar a base e tentar de novo.";
        }

        String message = result.output.trim();
        int lineBreak = message.indexOf('\n');
        if (lineBreak > 0) {
            message = message.substring(0, lineBreak).trim();
        }
        return message;
    }

    private static final class LaunchPreparation {
        final boolean launchGame;
        final boolean requiresDataUpdate;
        final String message;
        final int tabToOpen;

        private LaunchPreparation(boolean launchGame,
                                  boolean requiresDataUpdate,
                                  String message,
                                  int tabToOpen) {
            this.launchGame = launchGame;
            this.requiresDataUpdate = requiresDataUpdate;
            this.message = message;
            this.tabToOpen = tabToOpen;
        }

        static LaunchPreparation ready() {
            return new LaunchPreparation(true, false, null, -1);
        }

        static LaunchPreparation updateRequired() {
            return new LaunchPreparation(false, true, null, -1);
        }

        static LaunchPreparation blocked(String message, int tabToOpen) {
            return new LaunchPreparation(false, false, message, tabToOpen);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        Config.mainContext = this;
        mServersFavouriteList = new ArrayList<>();

        ConfigValidator.validateConfigFiles(this);
        ServerConfigManager.ensureSelectedServer(this);
        activeTabs = resolveActiveTabs();

        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        file.getParentFile().mkdirs();
        if (file.exists()) {
            file.delete();
        }

        boolean theme = mPref.getBoolean("theme", false);
        changeTheme(theme);

        FragmentManager fm = getSupportFragmentManager();
        ViewPagerAdapter sa = new ViewPagerAdapter(fm);
        pager = findViewById(R.id.fragment_place);
        pager.setAdapter(sa);

        // BotÃµes do menu
        ImageButton btnHome = findViewById(R.id.btn_home);
        ImageButton btnServers = findViewById(R.id.btn_servers);
        ImageButton btnHost = findViewById(R.id.btn_host);
        ImageButton btnFiles = findViewById(R.id.btn_files);
        ImageButton btnEditor = findViewById(R.id.btn_editor);
        ImageButton btnWorkbench = findViewById(R.id.btn_workbench);
        ImageButton btnDonate = findViewById(R.id.btn_donate);
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        ImageButton btnCredits = findViewById(R.id.btn_credits);

        configureNavButton(btnHome);
        configureNavButton(btnServers);
        configureNavButton(btnHost);
        configureNavButton(btnFiles);
        configureNavButton(btnEditor);
        configureNavButton(btnWorkbench);
        configureNavButton(btnDonate);
        configureNavButton(btnSettings);
        configureNavButton(btnCredits);

        configureNavShortcut(findViewById(R.id.nav_item_home), TAB_HOME);
        configureNavShortcut(findViewById(R.id.nav_item_servers), TAB_SERVERS);
        configureNavShortcut(findViewById(R.id.nav_item_host), TAB_HOST);
        configureNavShortcut(findViewById(R.id.nav_item_files), TAB_HOST_FILES);
        configureNavShortcut(findViewById(R.id.nav_item_editor), TAB_EDITOR);
        configureNavShortcut(findViewById(R.id.nav_item_workbench), TAB_WORKBENCH);
        configureNavShortcut(findViewById(R.id.nav_item_donate), TAB_STORE);
        configureNavShortcut(findViewById(R.id.nav_item_settings), TAB_SETTINGS);
        configureNavShortcut(findViewById(R.id.nav_item_credits), TAB_CREDITS);
        applyRoleNavigation();

        btnHome.setOnClickListener(v -> {
            openTab(TAB_HOME);
        });

        btnServers.setOnClickListener(v -> {
            openTab(TAB_SERVERS);
        });

        btnHost.setOnClickListener(v -> {
            openTab(TAB_HOST);
        });

        btnFiles.setOnClickListener(v -> {
            openTab(TAB_HOST_FILES);
        });

        btnEditor.setOnClickListener(v -> {
            openTab(TAB_EDITOR);
        });

        btnWorkbench.setOnClickListener(v -> {
            openTab(TAB_WORKBENCH);
        });

        btnDonate.setOnClickListener(v -> {
            openTab(TAB_STORE);
        });

        btnSettings.setOnClickListener(v -> {
            openTab(TAB_SETTINGS);
        });

        btnCredits.setOnClickListener(v -> {
            openTab(TAB_CREDITS);
        });

        openTab(resolveInitialTab());
    }

    public void openTab(int index) {
        int tabIndex = isTabAllowed(index) ? index : getDefaultTabForRole();
        int pagerPosition = findPagerPositionForTab(tabIndex);
        if (pagerPosition < 0) {
            return;
        }
        if (pager != null) {
            pager.setCurrentItem(pagerPosition);
        }
        updateSelectedTab(tabIndex);
    }

    public void changeTheme(boolean theme) {
        findViewById(R.id.main_layout).setBackgroundResource(R.drawable.launcher_app_background);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            int tabIndex = activeTabs != null && position >= 0 && position < activeTabs.length
                    ? activeTabs[position]
                    : TAB_HOME;
            switch (tabIndex) {
                case TAB_HOME:
                    return new HomeFragment();
                case TAB_SERVERS:
                    return new ServersFragment();
                case TAB_HOST:
                    return new HostFragment();
                case TAB_HOST_FILES:
                    return new HostFilesFragment();
                case TAB_EDITOR:
                    return new EditorFragment();
                case TAB_WORKBENCH:
                    return new WorkbenchFragment();
                case TAB_STORE:
                    return new DonateFragment();
                case TAB_SETTINGS:
                    return new SettingsFragment();
                case TAB_CREDITS:
                    return new CreditsFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getCount() {
            return activeTabs == null ? 0 : activeTabs.length;
        }
    }

    public boolean isTabAllowed(int tabIndex) {
        if (activeTabs == null) {
            return true;
        }

        for (int activeTab : activeTabs) {
            if (activeTab == tabIndex) {
                return true;
            }
        }
        return false;
    }

    private int[] resolveActiveTabs() {
        return new int[] {
                TAB_HOME,
                TAB_SERVERS,
                TAB_HOST,
                TAB_HOST_FILES,
                TAB_EDITOR,
                TAB_WORKBENCH,
                TAB_STORE,
                TAB_SETTINGS,
                TAB_CREDITS
        };
    }

    private int resolveInitialTab() {
        int requestedTab = getIntent().getIntExtra(EntryActivity.EXTRA_INITIAL_TAB, getDefaultTabForRole());
        return isTabAllowed(requestedTab) ? requestedTab : getDefaultTabForRole();
    }

    private int getDefaultTabForRole() {
        return TAB_HOME;
    }

    private int findPagerPositionForTab(int tabIndex) {
        if (activeTabs == null) {
            return -1;
        }

        for (int i = 0; i < activeTabs.length; i++) {
            if (activeTabs[i] == tabIndex) {
                return i;
            }
        }
        return -1;
    }

    private void applyRoleNavigation() {
        int[] tabs = {
                TAB_HOME,
                TAB_SERVERS,
                TAB_HOST,
                TAB_HOST_FILES,
                TAB_EDITOR,
                TAB_WORKBENCH,
                TAB_STORE,
                TAB_SETTINGS,
                TAB_CREDITS
        };

        int[] items = {
                R.id.nav_item_home,
                R.id.nav_item_servers,
                R.id.nav_item_host,
                R.id.nav_item_files,
                R.id.nav_item_editor,
                R.id.nav_item_workbench,
                R.id.nav_item_donate,
                R.id.nav_item_settings,
                R.id.nav_item_credits
        };

        for (int i = 0; i < tabs.length; i++) {
            View item = findViewById(items[i]);
            if (item != null) {
                item.setVisibility(isTabAllowed(tabs[i]) ? View.VISIBLE : View.GONE);
            }
        }
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }

    private boolean isToolsRole() {
        return "tools".equals(BuildConfig.XYRON_APK_ROLE);
    }

    private void updateSelectedTab(int selected) {
        int[] buttons = {
                R.id.btn_home,
                R.id.btn_servers,
                R.id.btn_host,
                R.id.btn_files,
                R.id.btn_editor,
                R.id.btn_workbench,
                R.id.btn_donate,
                R.id.btn_settings,
                R.id.btn_credits
        };

        int[] items = {
                R.id.nav_item_home,
                R.id.nav_item_servers,
                R.id.nav_item_host,
                R.id.nav_item_files,
                R.id.nav_item_editor,
                R.id.nav_item_workbench,
                R.id.nav_item_donate,
                R.id.nav_item_settings,
                R.id.nav_item_credits
        };

        int[] labels = {
                R.id.nav_label_home,
                R.id.nav_label_servers,
                R.id.nav_label_host,
                R.id.nav_label_files,
                R.id.nav_label_editor,
                R.id.nav_label_workbench,
                R.id.nav_label_donate,
                R.id.nav_label_settings,
                R.id.nav_label_credits
        };

        int activeLabelColor = getResources().getColor(R.color.launcher_accent_gold_bright);
        int inactiveLabelColor = getResources().getColor(R.color.launcher_text_muted);

        for (int i = 0; i < buttons.length; i++) {
            ImageButton btn = findViewById(buttons[i]);
            btn.setImageResource(i == selected ? tabSelectedImages[i] : tabImages[i]);
            btn.setBackgroundResource(i == selected
                    ? R.drawable.launcher_nav_button_active
                    : R.drawable.launcher_nav_button_idle);
            btn.setAlpha(i == selected ? 1.0f : 0.74f);

            View item = findViewById(items[i]);
            if (item != null) {
                item.setAlpha(i == selected ? 1.0f : 0.82f);
            }

            TextView label = findViewById(labels[i]);
            if (label != null) {
                label.setTextColor(i == selected ? activeLabelColor : inactiveLabelColor);
            }
        }
    }

    private void configureNavButton(ImageButton button) {
        if (button == null) {
            return;
        }
        button.setOnTouchListener(new ButtonAnimator(this, button));
    }

    private void configureNavShortcut(View shortcut, int tabIndex) {
        if (shortcut == null) {
            return;
        }
        shortcut.setOnTouchListener(new ButtonAnimator(this, shortcut));
        shortcut.setOnClickListener(v -> openTab(tabIndex));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
