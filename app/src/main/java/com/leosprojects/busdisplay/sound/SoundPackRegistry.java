package com.leosprojects.busdisplay.sound;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SoundPackRegistry {
    private static final String TAG = "BusSoundRegistry";
    private final Context context;
    private List<SoundPackProfile> profiles = Collections.emptyList();
    private String defaultProfileId;

    public SoundPackRegistry(Context context) { this.context = context.getApplicationContext(); }

    public List<SoundPackProfile> loadProfiles() {
        List<SoundPackProfile> loaded = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readAsset("sounds/profiles.json"));
            defaultProfileId = required(root, "defaultProfile");
            JSONArray entries = root.getJSONArray("profiles");
            for (int index = 0; index < entries.length(); index++) {
                try {
                    JSONObject item = entries.getJSONObject(index);
                    if (!item.optBoolean("enabled", true)) continue;
                    loaded.add(new SoundPackProfile(
                            required(item, "id"), required(item, "name"),
                            item.optString("description", ""), required(item, "folder"),
                            item.optBoolean("qa", false)));
                } catch (Exception error) {
                    Log.e(TAG, "Ignoring malformed sound profile at index " + index, error);
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Could not load sounds/profiles.json", error);
            loaded.clear();
            defaultProfileId = null;
        }
        profiles = Collections.unmodifiableList(loaded);
        return profiles;
    }

    public String getDefaultProfileId() { return defaultProfileId; }

    public SoundPackProfile findById(String id) {
        if (id == null) return null;
        for (SoundPackProfile profile : profiles) if (id.equals(profile.id)) return profile;
        return null;
    }

    private String readAsset(String path) throws Exception {
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String required(JSONObject object, String key) throws Exception {
        String value = object.getString(key).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
