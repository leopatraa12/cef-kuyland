package com.xyron.game.launcher.util;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

public class ButtonAnimator implements View.OnTouchListener {
    private static final float PRESSED_SCALE = 0.96f;
    private static final long PRESS_ANIMATION_MS = 65L;
    private static final long RELEASE_ANIMATION_MS = 85L;

    private final View target;

    public ButtonAnimator(Context context, View view) {
        this.target = view;
        view.setClickable(true);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            animateTo(PRESSED_SCALE, PRESS_ANIMATION_MS);
            return false;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            animateTo(1.0f, RELEASE_ANIMATION_MS);
        }
        return false;
    }

    private void animateTo(float scale, long durationMs) {
        target.animate()
                .cancel();
        target.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(durationMs)
                .start();
    }
}
