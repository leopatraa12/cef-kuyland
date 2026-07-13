package com.raiferoleplay.game.game;

/**
 * Native ABI bridge for recovered libSAMP builds that export JNI symbols under
 * the original com.raiferoleplay package.
 */
public class SAMP extends com.xyron.game.main.GTASA {
    protected native void initializeSAMP();

    protected native void onInputEnd(byte[] str);

    public native void onEventBackPressed();
}
