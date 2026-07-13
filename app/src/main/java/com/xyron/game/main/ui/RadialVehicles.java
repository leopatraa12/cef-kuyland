package com.xyron.game.main.ui;

import android.app.Activity;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.xyron.game.R;
import com.xyron.game.launcher.util.Util;

import java.nio.charset.StandardCharsets;

public class RadialVehicles {
    private ConstraintLayout radialLayout;
    private Activity activity;
    public static boolean menuVisibleV;
    private RadialMenu mRadialMenu;

    native void sendCommandV(byte[] str);

    public void show() {
        sendCommandV("/mv".getBytes(StandardCharsets.UTF_8));
    }
}