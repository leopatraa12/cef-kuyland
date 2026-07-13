package com.raiferoleplay.game.cef;

import android.util.Log;

import com.xyron.game.main.SAMP;

/**
 * Compatibility bridge for native builds that still resolve the legacy
 * com.raiferoleplay CEF entry point during JNI_OnLoad.
 */
public final class CefBrowser {
    private static final String TAG = "CefBrowserCompat";

    public static final int CEF_CREATE = 1;
    public static final int CEF_DESTROY = 2;
    public static final int CEF_LOAD_URL = 3;
    public static final int CEF_RELOAD = 4;
    public static final int CEF_STOP = 5;
    public static final int CEF_EVAL_JS = 6;
    public static final int CEF_SET_BOUNDS = 7;
    public static final int CEF_SET_VISIBLE = 8;
    public static final int CEF_SET_FOCUS = 9;
    public static final int CEF_SET_BG_ALPHA = 10;
    public static final int CEF_INPUT_MOUSE_MOVE = 11;
    public static final int CEF_INPUT_MOUSE_DOWN = 12;
    public static final int CEF_INPUT_MOUSE_UP = 13;
    public static final int CEF_INPUT_SCROLL = 14;
    public static final int CEF_INPUT_KEY_DOWN = 15;
    public static final int CEF_INPUT_KEY_UP = 16;
    public static final int CEF_INPUT_TEXT = 17;
    public static final int CEF_SET_INTERACTIVE = 18;

    public static final int CEF_EVENT_LOADED = 101;
    public static final int CEF_EVENT_URL_CHANGED = 102;
    public static final int CEF_EVENT_TITLE_CHANGED = 103;
    public static final int CEF_EVENT_CONSOLE = 104;
    public static final int CEF_EVENT_ERROR = 105;
    public static final int CEF_EVENT_CLICK = 106;

    private CefBrowser() {
    }

    public static void receiveCefPacket(int browserId, int actionId, String rawData) {
        Log.d(TAG, "CEF packet ignored by compatibility bridge: browserId="
                + browserId + " actionId=" + actionId + " data=" + rawData);
    }

    public static void initialize() {
        Log.i(TAG, "Legacy CEF bridge initialized");
    }

    public static void hideAllForPause() {
    }

    public static void restoreAfterResume() {
    }

    public static void shutdown() {
    }

    public static int[] getScreenSize() {
        SAMP activity = SAMP.getInstance();
        if (activity == null || activity.getResources() == null) {
            return new int[]{0, 0};
        }

        return new int[]{
                activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels
        };
    }

    public static byte[] captureWebView(int browserId) {
        return null;
    }

    public static native void nativeSendEvent(int browserId, int eventType, String data);
}
