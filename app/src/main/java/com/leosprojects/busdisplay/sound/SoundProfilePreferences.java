package com.leosprojects.busdisplay.sound;

import android.content.Context;
import android.content.SharedPreferences;

public final class SoundProfilePreferences {
    private static final String PREFS = "bus_sound_settings";
    private static final String KEY_PROFILE = "sound_profile";
    private final SharedPreferences preferences;

    public SoundProfilePreferences(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getSelectedProfileId() { return preferences.getString(KEY_PROFILE, null); }
    public void setSelectedProfileId(String id) {
        preferences.edit().putString(KEY_PROFILE, id).apply();
    }
}
