package com.wardrumstudios.utils;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.nvidia.devtech.NvUtil;

import java.io.File;
import java.util.Locale;

public class WarMedia extends WarGamepad {
    protected boolean AllowLongPressForExit = false;
    protected String apkFileName = "";
    public String baseDirectory = "";
    public String baseDirectoryRoot = "";
    protected String expansionFileName = "";
    protected String patchFileName = "";
    public XAPKFile[] xAPKS = null;

    private static final String PREFS_NAME = "xyron_public_war_media";
    private int currentLocale = Locale.getDefault().getLanguage().hashCode();

    public static class XAPKFile {
        public final boolean mIsMain;
        public final int mFileVersion;
        public final long mFileSize;

        public XAPKFile(boolean isMain, int fileVersion, long fileSize) {
            mIsMain = isMain;
            mFileVersion = fileVersion;
            mFileSize = fileSize;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        baseDirectory = GetGameBaseDirectory();
        NvUtil.getInstance().setAppLocalValue("STORAGE_ROOT", baseDirectory);
        NvUtil.getInstance().setAppLocalValue("STORAGE_ROOT_BASE", baseDirectoryRoot);
        super.onCreate(savedInstanceState);
        localHasGameData();
        NetworkChange();
    }

    protected void localHasGameData() {
        AfterDownloadFunction();
    }

    protected void NetworkChange() {
        NativeNotifyNetworkChange(isNetworkAvailable() ? 1 : 0);
    }

    private native void initTouchSense(Context context);

    public native void NativeNotifyNetworkChange(int state);

    public native void setTouchSenseFilepath(String path);

    public String GetGameBaseDirectory() {
        File external = getExternalFilesDir(null);
        File base = external != null ? external : getFilesDir();
        String absolutePath = base.getAbsolutePath();
        int androidIndex = absolutePath.indexOf(File.separator + "Android");
        if (androidIndex > 0) {
            baseDirectoryRoot = absolutePath.substring(0, androidIndex);
        } else {
            baseDirectoryRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return ensureTrailingSlash(base.getAbsolutePath());
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities != null
                    && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }

        android.net.NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public boolean isWiFiAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }

        android.net.NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
    }

    public boolean isTV() {
        UiModeManager manager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public boolean IsTV() {
        return isTV();
    }

    public boolean IsPhone() {
        return !isTV();
    }

    public String GetPackageName(String appname) {
        return getApplicationInfo().sourceDir;
    }

    public void RestoreCurrentLanguage() {
        currentLocale = GetDeviceLocale();
    }

    public void SetLocale(String languageTag) {
        if (languageTag == null || languageTag.trim().isEmpty()) {
            return;
        }

        currentLocale = languageTag.trim().hashCode();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("locale", languageTag.trim())
                .apply();
    }

    public void SetLocale(int newLanguage) {
        currentLocale = newLanguage;
    }

    public int GetLocale() {
        return currentLocale;
    }

    public int GetDeviceLocale() {
        return Locale.getDefault().toLanguageTag().hashCode();
    }

    public void GetRealLocale() {
        currentLocale = GetDeviceLocale();
    }

    public boolean DeleteFile(String filename) {
        return filename != null && new File(filename).delete();
    }

    public boolean FileRename(String oldFile, String newFile, int overwrite) {
        if (oldFile == null || newFile == null) {
            return false;
        }

        File source = new File(oldFile);
        File target = new File(newFile);
        if (target.exists() && overwrite == 0) {
            return false;
        }

        return source.renameTo(target);
    }

    public String FileGetArchiveName(int type) {
        switch (type) {
            case 0:
                return apkFileName;
            case 1:
                return expansionFileName;
            case 2:
                return patchFileName;
            default:
                return "";
        }
    }

    public String GetConfigSetting(String key) {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, "");
    }

    public void SetConfigSetting(String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(key, value == null ? "" : value);
        editor.apply();
    }

    public String OBFU_GetDeviceID() {
        return Build.MANUFACTURER + "-" + Build.MODEL;
    }

    public String GetAndroidBuildinfo(int index) {
        switch (index) {
            case 0:
                return Build.MANUFACTURER;
            case 1:
                return Build.MODEL;
            case 2:
                return Build.VERSION.RELEASE;
            default:
                return "";
        }
    }

    public int GetDeviceInfo(int index) {
        switch (index) {
            case 0:
                return Build.VERSION.SDK_INT;
            case 1:
                return Runtime.getRuntime().availableProcessors();
            default:
                return 0;
        }
    }

    public int GetDeviceType() {
        return isTV() ? 2 : 1;
    }

    public void ShowKeyboard(int show) {
        InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input == null || getWindow() == null || getWindow().getDecorView() == null) {
            return;
        }

        if (show != 0) {
            input.showSoftInput(getWindow().getDecorView(), InputMethodManager.SHOW_IMPLICIT);
        } else {
            input.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        }
    }

    public boolean IsKeyboardShown() {
        return false;
    }

    public String GetPackageName() {
        return getPackageName();
    }

    public boolean IsAppInstalled(String appname) {
        if (appname == null || appname.trim().isEmpty()) {
            return false;
        }

        try {
            getPackageManager().getPackageInfo(appname, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public void OpenLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public boolean IsCloudAvailable() {
        return false;
    }

    public void LoadAllGamesFromCloud() {
    }

    public String LoadGameFromCloud(int slot, byte[] array) {
        return "";
    }

    public void SaveGameToCloud(int slot, byte[] array, int numbytes) {
    }

    public boolean NewCloudSaveAvailable(int slot) {
        return false;
    }

    public boolean CustomLoadFunction() {
        return false;
    }

    public void AfterDownloadFunction() {
    }

    public void SendStatEvent(String eventId, boolean timedEvent) {
    }

    public void SendTimedStatEventEnd(String eventId) {
    }

    public void SendStatEvent(String eventId, String paramName, String paramValue, boolean timedEvent) {
    }

    public int GetTotalMemory() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return 0;
        }

        manager.getMemoryInfo(info);
        return (int) (info.totalMem / (1024L * 1024L));
    }

    public int GetAvailableMemory() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return 0;
        }

        manager.getMemoryInfo(info);
        return (int) (info.availMem / (1024L * 1024L));
    }

    public int GetMemoryInfo(boolean allProcesses) {
        return GetAvailableMemory();
    }

    public int GetLowThreshhold() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return 0;
        }

        manager.getMemoryInfo(info);
        return (int) (info.threshold / (1024L * 1024L));
    }

    public float GetScreenWidthInches() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels / metrics.xdpi;
    }

    public String GetAppId() {
        return getPackageName();
    }

    public void ScreenSetWakeLock(boolean enable) {
        if (enable) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void VibratePhone(int milliseconds) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && milliseconds > 0) {
            vibrator.vibrate(milliseconds);
        }
    }

    public void VibratePhoneEffect(int effect) {
        VibratePhone(30);
    }

    public void ClearSystemNotification() {
    }

    public void MovieSetSkippable(boolean skippable) {
    }

    public void StopMovie() {
    }

    public void PlayMovieInWindow(String filename, int x, int y, int width, int height,
                                  float volume, int offset, int length, int looping,
                                  boolean forceSize) {
    }

    public void PlayMovieInFile(String filename, float volume, int offset, int length) {
    }

    public void PlayMovie(String filename, float volume) {
    }

    public int IsMoviePlaying() {
        return 0;
    }

    public void MovieKeepAspectRatio(boolean keep) {
    }

    public void MovieSetTextScale(int scale) {
    }

    public void MovieClearText(boolean isSubtitle) {
    }

    public void MovieSetText(String text, boolean displayNow, boolean isSubtitle) {
    }

    public void MovieDisplayText(boolean display) {
    }

    public int GetSpecialBuildType() {
        return 0;
    }

    public void CreateTextBox(int id, int x, int y, int x2, int y2) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean CheckIfNeedsReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return false;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 8001);
        return true;
    }

    public boolean CheckIfNeedsBluetoothPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 8002);
        return true;
    }

    public boolean ConvertToBitmap(byte[] data, int length) {
        return data != null && length > 0
                && BitmapFactory.decodeByteArray(data, 0, Math.min(data.length, length)) != null;
    }

    public boolean ServiceAppCommand(String cmd, String args) {
        return false;
    }

    public int ServiceAppCommandValue(String cmd, String args) {
        return 0;
    }

    public boolean ServiceAppCommandInt(String cmd, int args) {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (AllowLongPressForExit && keyCode == KeyEvent.KEYCODE_BACK && event != null && event.isLongPress()) {
            finish();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public long GetFreeBytesOnExternalStorage() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        return stat.getAvailableBytes();
    }

    private static String ensureTrailingSlash(String path) {
        if (path.endsWith(File.separator)) {
            return path;
        }

        return path + File.separator;
    }
}
