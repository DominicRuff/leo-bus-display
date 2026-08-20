package com.leosprojects.busdisplay.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SoundPack {
    public static final class Gear {
        public final int number;
        public final String assetPath;
        public final float rateMin;
        public final float rateMax;
        public final float gain;
        public final int speedMin;
        public final int speedMax;
        public final int downshiftBelow;

        public Gear(int number, String assetPath, float rateMin, float rateMax,
                    float gain, int speedMin, int speedMax, int downshiftBelow) {
            this.number = number;
            this.assetPath = assetPath;
            this.rateMin = rateMin;
            this.rateMax = rateMax;
            this.gain = gain;
            this.speedMin = speedMin;
            this.speedMax = speedMax;
            this.downshiftBelow = downshiftBelow;
        }

        public float playbackRate(float rpmFraction) {
            float bounded = Math.max(0f, Math.min(1f, rpmFraction));
            return rateMin + (rateMax - rateMin) * bounded;
        }
    }

    public final String id;
    public final String name;
    public final String variant;
    public final String manufacturer;
    public final String model;
    public final String transmission;
    public final int declaredGears;
    public final Gear idle;
    public final List<Gear> gears;
    public final int crossfadeMs;
    public final Map<String, String> shiftAssets;
    public final String representativeShiftAsset;
    public final String downshiftAsset;
    public final String engineStartAsset;
    public final long engineStartDurationMs;
    public final String stoppingAsset;
    public final long stoppingDurationMs;
    public final String brakeAsset;
    public final String hornAsset;
    public final String doorsOpenAsset;
    public final String doorsCloseAsset;

    public SoundPack(String id, String name, String variant, String manufacturer,
                     String model, String transmission, int declaredGears, Gear idle,
                     List<Gear> gears, int crossfadeMs, Map<String, String> shiftAssets,
                     String representativeShiftAsset, String downshiftAsset,
                     String engineStartAsset, long engineStartDurationMs,
                     String stoppingAsset, long stoppingDurationMs,
                     String brakeAsset, String hornAsset, String doorsOpenAsset,
                     String doorsCloseAsset) {
        this.id = id;
        this.name = name;
        this.variant = variant;
        this.manufacturer = manufacturer;
        this.model = model;
        this.transmission = transmission;
        this.declaredGears = declaredGears;
        this.idle = idle;
        this.gears = Collections.unmodifiableList(new ArrayList<>(gears));
        this.crossfadeMs = crossfadeMs;
        this.shiftAssets = Collections.unmodifiableMap(new LinkedHashMap<>(shiftAssets));
        this.representativeShiftAsset = representativeShiftAsset;
        this.downshiftAsset = downshiftAsset;
        this.engineStartAsset = engineStartAsset;
        this.engineStartDurationMs = engineStartDurationMs;
        this.stoppingAsset = stoppingAsset;
        this.stoppingDurationMs = stoppingDurationMs;
        this.brakeAsset = brakeAsset;
        this.hornAsset = hornAsset;
        this.doorsOpenAsset = doorsOpenAsset;
        this.doorsCloseAsset = doorsCloseAsset;
    }

    public Gear getGear(int gearNumber) {
        if (gearNumber <= 0) return idle;
        for (Gear gear : gears) if (gear.number == gearNumber) return gear;
        return null;
    }

    public String shiftAsset(int fromGear, int toGear) {
        return shiftAssets.get(fromGear + "-" + toGear);
    }

    public List<String> declaredAssets() {
        List<String> paths = new ArrayList<>();
        add(paths, idle == null ? null : idle.assetPath);
        for (Gear gear : gears) add(paths, gear.assetPath);
        for (String path : shiftAssets.values()) add(paths, path);
        add(paths, representativeShiftAsset);
        add(paths, downshiftAsset);
        add(paths, engineStartAsset);
        add(paths, stoppingAsset);
        add(paths, brakeAsset);
        add(paths, hornAsset);
        add(paths, doorsOpenAsset);
        add(paths, doorsCloseAsset);
        return paths;
    }

    private static void add(List<String> paths, String path) {
        if (path != null && !paths.contains(path)) paths.add(path);
    }
}
