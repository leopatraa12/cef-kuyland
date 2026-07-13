package com.xyron.game.launcher.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

public final class ViewPagerWithoutSwipe extends ViewPager {
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        // Retorna falso para nÃ£o interceptar o toque e impedir o arraste
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Retorna falso para ignorar o evento de toque de arraste
        return false;
    }

    public ViewPagerWithoutSwipe(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }
}
