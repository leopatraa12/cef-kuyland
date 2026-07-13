package com.xyron.game.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.xyron.game.launcher.util.ConfigValidator;
import com.xyron.game.launcher.util.ServerConfigManager;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class EntryActivity extends SampActivity {
    public static final String EXTRA_INITIAL_TAB = "initial_tab";
    public static final String EXTRA_FORCE_UPDATE_DATA = "force_update_data";

    private static final String EXTRA_SERVER_IP = "server_ip";
    private static final String EXTRA_SERVER_PORT = "server_port";
    private static final String EXTRA_NICKNAME = "nickname";
    private static final String TAG = "EntryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConfigValidator.validateConfigFiles(this);
        applyIncomingConnection(getIntent());

        boolean forceUpdate = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_FORCE_UPDATE_DATA, false);
        if (forceUpdate) {
            Intent updateIntent = new Intent(this, UpdateActivity.class);
            updateIntent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(updateIntent);
            finish();
            return;
        }

        Intent gameIntent = new Intent(this, MainActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            gameIntent.putExtras(getIntent().getExtras());
        }
        gameIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(gameIntent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void applyIncomingConnection(Intent intent) {
        if (intent == null) {
            return;
        }

        String host = sanitize(intent.getStringExtra(EXTRA_SERVER_IP));
        int port = intent.getIntExtra(EXTRA_SERVER_PORT, 0);
        if (!TextUtils.isEmpty(host) && port > 0 && port <= 65535) {
            ServerConfigManager.ServerOption option = ServerConfigManager.addOrUpdateServer(
                    this,
                    "Launcher",
                    host,
                    port,
                    true
            );
            if (option != null && option.isValid()) {
                ServerConfigManager.saveSelectedServer(this, option);
            }
        }

        String nickname = sanitize(intent.getStringExtra(EXTRA_NICKNAME));
        if (!TextUtils.isEmpty(nickname)) {
            saveNickname(nickname);
        }
    }

    private void saveNickname(String nickname) {
        File settingsFile = new File(getExternalFilesDir(null), "SAMP/settings.ini");
        File parent = settingsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Could not create settings directory.");
            return;
        }

        try {
            if (!settingsFile.exists() && !settingsFile.createNewFile()) {
                Log.w(TAG, "Could not create settings.ini.");
                return;
            }

            Wini wini = new Wini(settingsFile);
            wini.put("client", "name", nickname);
            wini.store();
        } catch (IOException e) {
            Log.e(TAG, "Could not save incoming nickname.", e);
        }
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
