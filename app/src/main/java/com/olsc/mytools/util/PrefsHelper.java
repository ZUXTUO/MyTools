package com.olsc.mytools.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsHelper {
    private static final String PREF_NAME = "MyToolsPrefs";
    private static final String KEY_BRIGHTNESS = "brightness_value";
    private static final String KEY_VOLUME = "volume_value";
    private static final String KEY_NOTIF_ENABLED = "notif_enabled";
    private static final String KEY_DOT_COLOR = "dot_color";

    private final SharedPreferences prefs;

    public PrefsHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveBrightness(int value) {
        if (value >= 20) {
            prefs.edit().putInt(KEY_BRIGHTNESS, value).apply();
        } else {
            // Requirement: if < 20%, default restore 100%
            prefs.edit().putInt(KEY_BRIGHTNESS, 100).apply();
        }
    }

    public int getBrightness() {
        return prefs.getInt(KEY_BRIGHTNESS, 100);
    }

    public void saveVolume(int value) {
        if (value >= 20) {
            prefs.edit().putInt(KEY_VOLUME, value).apply();
        } else {
            // Requirement: if < 20%, default restore 100%
            prefs.edit().putInt(KEY_VOLUME, 100).apply();
        }
    }

    public int getVolume() {
        return prefs.getInt(KEY_VOLUME, 100);
    }

    public void setNotifEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply();
    }

    public boolean isNotifEnabled() {
        return prefs.getBoolean(KEY_NOTIF_ENABLED, false);
    }

    public void setDotColor(int color) {
        prefs.edit().putInt(KEY_DOT_COLOR, color).apply();
    }

    public int getDotColor() {
        return prefs.getInt(KEY_DOT_COLOR, android.graphics.Color.WHITE);
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
