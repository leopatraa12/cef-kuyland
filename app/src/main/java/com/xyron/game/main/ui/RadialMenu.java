package com.xyron.game.main.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;

import com.xyron.game.R;
import com.xyron.game.launcher.util.Util;

import com.xyron.game.main.SAMP;

import java.nio.charset.StandardCharsets;

public class RadialMenu {
    private static final int RADIAL_ICON_COLOR = 0xFFF59E0B;
    private static final int RADIAL_TEXT_COLOR = 0xFFF6EAD8;
    private static final int RADIAL_BACKDROP_COLOR = 0x66120B18;
    private static final int RADIAL_TEXT_SHADOW = 0x70000000;
    private ConstraintLayout radialLayout;
    private Activity activity;
    public static boolean menuVisible;
    private RadialVehicles mRadialVehicles;
    //int RadinhoSom;

    native void sendCommand(byte[] str);

    public RadialMenu(Activity activity) {
        this.activity = activity;

        // Inflar o layout radial
        ConstraintLayout layout = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.radial, null);
        activity.addContentView(layout, new ConstraintLayout.LayoutParams(-1, -1));

        radialLayout = activity.findViewById(R.id.radial);
        radialLayout.setVisibility(View.GONE);
        applyTheme();

        //Som de radinho
        //RadinhoSom = SAMP.soundPool.load(SAMP.getInstance(), R.raw.somradinho, 0);

        // Configurar listeners para os botÃµes
        setListeners();
        menuVisible = false;

        // Esconder o layout inicialmente
        Util.HideLayout(radialLayout, false);
    }

    private void applyTheme() {
        Typeface labelTypeface = ResourcesCompat.getFont(activity, R.font.montserrat_regular);
        int[] iconIds = {
                R.id.radial_image_00,
                R.id.radial_image_01,
                R.id.radial_image_02,
                R.id.radial_image_03,
                R.id.radial_image_04,
                R.id.radial_image_05,
                R.id.radial_image_06,
                R.id.radial_image_07,
                R.id.radial_image_08,
                R.id.radial_image_09
        };
        int[] labelIds = {
                R.id.radial_text_00,
                R.id.radial_text_01,
                R.id.radial_text_02,
                R.id.radial_text_03,
                R.id.radial_text_04,
                R.id.radial_text_05,
                R.id.radial_text_06,
                R.id.radial_text_07,
                R.id.radial_text_08,
                R.id.radial_text_09
        };

        radialLayout.setBackgroundColor(RADIAL_BACKDROP_COLOR);

        ImageView closeIcon = activity.findViewById(R.id.imageView31);
        closeIcon.setImageTintList(ColorStateList.valueOf(RADIAL_TEXT_COLOR));

        for (int iconId : iconIds) {
            ImageView icon = activity.findViewById(iconId);
            icon.setImageTintList(ColorStateList.valueOf(RADIAL_ICON_COLOR));
        }

        for (int labelId : labelIds) {
            TextView label = activity.findViewById(labelId);
            label.setTextColor(RADIAL_TEXT_COLOR);
            label.setIncludeFontPadding(false);
            label.setSingleLine(true);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.25f);
            label.setShadowLayer(10f, 0f, 0f, RADIAL_TEXT_SHADOW);
            if (labelTypeface != null) {
                label.setTypeface(labelTypeface);
            }
        }
    }

    private void setListeners()
    {
        // BotÃ£o central para fechar o menu
        activity.findViewById(R.id.radial_close).setOnClickListener(view -> {
            hide();
        });

        // Configurar cliques nos botÃµes radiais
        setRadialButtonClick(R.id.radial_button_00, 0);
        setRadialButtonClick(R.id.radial_button_01, 1);
        setRadialButtonClick(R.id.radial_button_02, 2);
        setRadialButtonClick(R.id.radial_button_03, 3); // BotÃ£o 3 configurado
        setRadialButtonClick(R.id.radial_button_04, 4);
        setRadialButtonClick(R.id.radial_button_05, 5);
        setRadialButtonClick(R.id.radial_button_06, 6);
        setRadialButtonClick(R.id.radial_button_07, 7);
        setRadialButtonClick(R.id.radial_button_08, 8);
        setRadialButtonClick(R.id.radial_button_09, 9);
    }

    private void setRadialButtonClick(int buttonId, int actionId)
    {
        activity.findViewById(buttonId).setOnClickListener(view -> {
            if (actionId == 0)//Celular
            {
                hide();
                if (activity instanceof SAMP) {
                    ((SAMP) activity).showPhoneOverlay();
                    return;
                }
                sendCommand("/c".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 1)//Gps
            {
                hide();
                sendCommand("/gps".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 2)//Mochila
            {
                hide();
                if (activity instanceof SAMP) {
                    ((SAMP) activity).showInventoryOverlay();
                    return;
                }
                sendCommand("/m".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 3)//eloc(era TAB)
            {
                hide();
                sendCommand("/eloc".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 4)//Propriedades
            {
                hide();
                sendCommand("/casa".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 5)//Missoes
            {
                hide();
                sendCommand("/ajuda".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 6)//Loja Vip
            {
                hide();
                sendCommand("/loja".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 7)//Atendimento
            {
                hide();
                sendCommand("/atm".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 8)//Animacoes
            {
                hide();
                sendCommand("/anim".getBytes(StandardCharsets.UTF_8));
            }
            if (actionId == 9)//Registro RG
            {
                hide();
                sendCommand("/doc".getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    public void show() {
        if(!menuVisible && !RadialVehicles.menuVisibleV)
        {
            Util.ShowLayout(radialLayout, true);
            menuVisible = true;
        }
    }

    public void hide() {
        if(menuVisible)
        {
            Util.HideLayout(radialLayout, true);
            menuVisible = false;
        }
    }
}
