package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigValidator {
    private static final String PREFS_NAME = "xyron_config_validator";
    private static final String KEY_TOUCH_LAYOUT_VERSION = "touch_layout_version";
    private static final int TOUCH_LAYOUT_VERSION = 20260509;
    private static final long MIN_CORE_TEXT_BYTES = 1024L;
    private static final String CORE_TEXT_ASSET_PATH = "Text/american.gxt";
    private static final String CORE_TEXT_OUTPUT_PATH = "TEXT/AMERICAN.GXT";
    private static final String[] TOUCH_LAYOUT_FILES = new String[] {
            "data/TouchDefaultPhoneWidescreen.cfg",
            "data/TouchDefaultPhone3x2.cfg",
            "data/TouchDefaultTabletWidescreen.cfg",
            "data/TouchDefaultTablet4x3.cfg",
            "data/360Default1280x720.cfg",
            "data/360Default960x720.cfg"
    };

    public static void validateConfigFiles(Context context) {
        Context appContext = context.getApplicationContext();
        File externalFilesDir = appContext.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            return;
        }

        ensureCoreTextDatabase(appContext.getAssets(), externalFilesDir);

        File file = new File(externalFilesDir, "SAMP/settings.ini");
        if (!file.exists()) {
            ensureParentDirectory(file);
            copyAsset(appContext.getAssets(), "settings.ini", file.toString());
        }

        SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedVersion = preferences.getInt(KEY_TOUCH_LAYOUT_VERSION, 0);
        if (savedVersion != TOUCH_LAYOUT_VERSION || !touchLayoutAssetsExist(externalFilesDir)) {
            syncTouchLayoutAssets(appContext.getAssets(), externalFilesDir);
        }
        invalidateTouchLayoutCacheIfNeeded(preferences, savedVersion, externalFilesDir);

        /*File file2 = new File(externalFilesDir, "gta_sa.set");
        if (!file2.exists()) {
            file2.getParentFile().mkdirs();
            copyAsset(context.getAssets(), "gta_sa.set", file2.toString());
        }*/
    }

    private static void ensureCoreTextDatabase(AssetManager assetManager, File externalFilesDir) {
        File textDatabase = new File(externalFilesDir, CORE_TEXT_OUTPUT_PATH);
        if (textDatabase.exists() && textDatabase.length() >= MIN_CORE_TEXT_BYTES) {
            return;
        }

        copyAsset(assetManager, CORE_TEXT_ASSET_PATH, textDatabase.toString());
    }

    private static void syncTouchLayoutAssets(AssetManager assetManager, File externalFilesDir) {
        for (String relativePath : TOUCH_LAYOUT_FILES) {
            copyAsset(assetManager, relativePath, new File(externalFilesDir, relativePath).toString());
            copyAsset(assetManager, relativePath, new File(externalFilesDir, "files/" + relativePath).toString());
        }
    }

    private static boolean touchLayoutAssetsExist(File externalFilesDir) {
        for (String relativePath : TOUCH_LAYOUT_FILES) {
            if (!new File(externalFilesDir, relativePath).exists()
                    || !new File(externalFilesDir, "files/" + relativePath).exists()) {
                return false;
            }
        }
        return true;
    }

    private static void invalidateTouchLayoutCacheIfNeeded(SharedPreferences preferences,
                                                           int savedVersion,
                                                           File externalFilesDir) {
        if (savedVersion == TOUCH_LAYOUT_VERSION) {
            return;
        }

        deleteIfExists(new File(externalFilesDir, "gtasatelem.set"));
        deleteIfExists(new File(externalFilesDir, "files/gtasatelem.set"));

        preferences.edit()
                .putInt(KEY_TOUCH_LAYOUT_VERSION, TOUCH_LAYOUT_VERSION)
                .apply();
    }

    static boolean copyAsset(AssetManager assetManager, String str, String str2) {
        File outputFile = new File(str2);
        ensureParentDirectory(outputFile);
        if (outputFile.exists() && !outputFile.delete()) {
            return false;
        }

        try (InputStream open = assetManager.open(str);
             FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            copyFile(open, fileOutputStream);
            fileOutputStream.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static void copyFile(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[8192];
        while (true) {
            int read = inputStream.read(bArr);
            if (read != -1) {
                outputStream.write(bArr, 0, read);
            } else {
                return;
            }
        }
    }

    private static void ensureParentDirectory(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static void deleteIfExists(File file) {
        if (file.exists()) {
            file.delete();
        }
    }
}
