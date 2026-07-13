package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Locale;

public final class DataVariantPreferences {
    public static final String DATA_VARIANT_LITE = "lite";
    public static final String DATA_VARIANT_FULL = "full";

    private static final String PREF_SELECTED_DATA_VARIANT = "selected_data_variant";

    private DataVariantPreferences() {
    }

    public static String getSelectedVariantId(Context context) {
        if (context == null) {
            return "";
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String variantId = normalizeVariantId(preferences.getString(PREF_SELECTED_DATA_VARIANT, ""));
        return isSupportedVariantId(variantId) ? variantId : "";
    }

    public static String getSelectedVariantIdOrDefault(Context context) {
        String variantId = getSelectedVariantId(context);
        return variantId.isEmpty() ? DATA_VARIANT_LITE : variantId;
    }

    public static void saveSelectedVariantId(Context context, String variantId) {
        if (context == null) {
            return;
        }

        String normalizedVariantId = normalizeVariantId(variantId);
        if (!isSupportedVariantId(normalizedVariantId)) {
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_SELECTED_DATA_VARIANT, normalizedVariantId)
                .apply();
    }

    public static boolean isSupportedVariantId(String variantId) {
        String normalizedVariantId = normalizeVariantId(variantId);
        return DATA_VARIANT_LITE.equals(normalizedVariantId)
                || DATA_VARIANT_FULL.equals(normalizedVariantId);
    }

    public static String normalizeVariantId(String variantId) {
        return variantId == null ? "" : variantId.trim().toLowerCase(Locale.US);
    }
}
