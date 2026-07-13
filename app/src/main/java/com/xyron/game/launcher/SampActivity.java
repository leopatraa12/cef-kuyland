package com.xyron.game.launcher;


import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.xyron.game.R;

import java.util.Map;


public class SampActivity extends AppCompatActivity {


    public SharedPreferences mPref;
    Toast mToast;

    boolean isDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ForÃ§ar modo imersivo/full screen de forma segura (evita o crash NPE)
        applyFullScreen();

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullScreen();
        }
    }

    private void applyFullScreen() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    public static void putSettingSwitchToPref(SharedPreferences pref, String tag, boolean state) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(tag, state);
        editor.apply();
    }
    boolean isNetworkConnected(){

        ConnectivityManager connMng = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMng == null) return false;
        NetworkInfo networkInfo = connMng.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}