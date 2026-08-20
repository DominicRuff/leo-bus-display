package com.leosprojects.busdisplay.sound;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SoundPackLoader {
    private final Context context;

    public SoundPackLoader(Context context) { this.context = context.getApplicationContext(); }

    public SoundPack load(SoundPackProfile profile) throws Exception {
        JSONObject root = new JSONObject(readAsset(profile.folder + "/soundpack.json"));
        if (root.getInt("schemaVersion") != 1) throw new IllegalArgumentException("Unsupported schema");
        String id = root.getString("id");
        if (!profile.id.equals(id)) throw new IllegalArgumentException("Profile ID mismatch");
        JSONObject vehicle = root.getJSONObject("vehicle");
        JSONObject engine = root.getJSONObject("engine");
        JSONObject idleJson = engine.optJSONObject("idle");
        SoundPack.Gear idle = idleJson == null ? null : parseGear(profile, 0, idleJson);
        JSONArray gearArray = engine.getJSONArray("gears");
        List<SoundPack.Gear> gears = new ArrayList<>();
        for (int index = 0; index < gearArray.length(); index++) {
            JSONObject gear = gearArray.getJSONObject(index);
            gears.add(parseGear(profile, gear.getInt("gear"), gear));
        }
        JSONObject transmission = root.getJSONObject("transmission");
        Map<String, String> shifts = new LinkedHashMap<>();
        for (int from = 1; from < gears.size(); from++) {
            String key = "shift" + from + (from + 1);
            String path = optionalPath(profile, transmission, key);
            if (path != null) shifts.put(from + "-" + (from + 1), path);
        }
        JSONObject effects = root.getJSONObject("effects");
        long stoppingDurationMs = effects.optLong("stoppingDurationMs", 0);
        if (stoppingDurationMs < 0) {
            throw new IllegalArgumentException("stoppingDurationMs must be non-negative");
        }
        return new SoundPack(
                id, root.getString("name"), root.optString("variant", ""),
                vehicle.optString("manufacturer", ""), vehicle.optString("model", ""),
                vehicle.optString("transmission", ""), vehicle.optInt("gears", gears.size()),
                idle, gears, transmission.optInt("crossfadeMs", 260), shifts,
                optionalPath(profile, transmission, "representativeShift"),
                optionalPath(profile, transmission, "downshift"),
                optionalPath(profile, effects, "engineStart"),
                effects.optLong("engineStartDurationMs", 0),
                optionalPath(profile, effects, "stopping"), stoppingDurationMs,
                optionalPath(profile, effects, "brake"), optionalPath(profile, effects, "horn"),
                optionalPath(profile, effects, "doorsOpen"),
                optionalPath(profile, effects, "doorsClose"));
    }

    private SoundPack.Gear parseGear(SoundPackProfile profile, int number,
                                     JSONObject json) throws Exception {
        return new SoundPack.Gear(number, resolve(profile, json.getString("file")),
                (float) json.optDouble("rateMin", json.optDouble("rate", 1.0)),
                (float) json.optDouble("rateMax", json.optDouble("rate", 1.0)),
                (float) json.optDouble("gain", 1.0), json.optInt("speedMin", 0),
                json.optInt("speedMax", 80), json.optInt("downshiftBelow", 0));
    }

    private String optionalPath(SoundPackProfile profile, JSONObject json, String key) {
        if (json.isNull(key)) return null;
        String file = json.optString(key, "").trim();
        return file.isEmpty() ? null : resolve(profile, file);
    }

    private String resolve(SoundPackProfile profile, String file) {
        return profile.folder + "/" + file;
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
}
