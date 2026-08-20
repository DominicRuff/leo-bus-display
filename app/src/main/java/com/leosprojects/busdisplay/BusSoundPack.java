package com.leosprojects.busdisplay;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BusSoundPack {
    public enum EngineMode { SINGLE_LOOP, LAYERED }

    public static final BusSoundPack GTT_CLASSIC_BUS = new BusSoundPack(
            "gtt_classic_bus",
            "GTT Classic Bus",
            EngineMode.LAYERED,
            R.raw.gtt_engine_start,
            new int[] {
                    R.raw.gtt_idle, R.raw.gtt_gear1, R.raw.gtt_gear2,
                    R.raw.gtt_gear3, R.raw.gtt_gear4, R.raw.gtt_gear5
            },
            new int[] {
                    R.raw.gtt_shift_1_2, R.raw.gtt_shift_2_3,
                    R.raw.gtt_shift_3_4, R.raw.gtt_shift_4_5
            },
            R.raw.gtt_downshift,
            R.raw.gtt_brake,
            R.raw.prototype_horn,
            2600
    );

    public static final BusSoundPack PROTOTYPE_DIESEL_TEST = new BusSoundPack(
            "prototype_diesel_test",
            "Prototype Diesel Test",
            EngineMode.SINGLE_LOOP,
            R.raw.prototype_engine_start,
            new int[] {R.raw.prototype_engine_loop},
            new int[] {R.raw.prototype_gear_shift},
            0,
            0,
            R.raw.prototype_horn,
            850
    );

    private static final List<BusSoundPack> PACKS = Collections.unmodifiableList(
            Arrays.asList(GTT_CLASSIC_BUS, PROTOTYPE_DIESEL_TEST));

    private final String id;
    private final String displayName;
    private final EngineMode engineMode;
    private final int engineStartResource;
    private final int[] engineLoopResources;
    private final int[] upshiftResources;
    private final int downshiftResource;
    private final int brakeResource;
    private final int hornResource;
    private final long engineStartDurationMs;

    private BusSoundPack(String id, String displayName, EngineMode engineMode,
                         int engineStartResource, int[] engineLoopResources,
                         int[] upshiftResources, int downshiftResource, int brakeResource,
                         int hornResource, long engineStartDurationMs) {
        this.id = id;
        this.displayName = displayName;
        this.engineMode = engineMode;
        this.engineStartResource = engineStartResource;
        this.engineLoopResources = engineLoopResources.clone();
        this.upshiftResources = upshiftResources.clone();
        this.downshiftResource = downshiftResource;
        this.brakeResource = brakeResource;
        this.hornResource = hornResource;
        this.engineStartDurationMs = engineStartDurationMs;
    }

    public static List<BusSoundPack> packs() { return PACKS; }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public EngineMode engineMode() { return engineMode; }
    public boolean isLayered() { return engineMode == EngineMode.LAYERED; }
    public int engineStartResource() { return engineStartResource; }
    public int engineLoopResource() { return engineLoopResources[0]; }
    public int engineLoopResource(int band) {
        int index = Math.max(0, Math.min(engineLoopResources.length - 1, band));
        return engineLoopResources[index];
    }
    public int gearChangeResource() { return upshiftResources[0]; }
    public int upshiftResourceForBand(int newBand) {
        if (newBand < 2 || newBand > 5 || upshiftResources.length < 4) return 0;
        return upshiftResources[newBand - 2];
    }
    public int downshiftResource() { return downshiftResource; }
    public int brakeResource() { return brakeResource; }
    public int hornResource() { return hornResource; }
    public long engineStartDurationMs() { return engineStartDurationMs; }

    public int[] allResources() {
        Set<Integer> resources = new LinkedHashSet<>();
        resources.add(engineStartResource);
        for (int resource : engineLoopResources) resources.add(resource);
        for (int resource : upshiftResources) resources.add(resource);
        if (downshiftResource != 0) resources.add(downshiftResource);
        if (brakeResource != 0) resources.add(brakeResource);
        resources.add(hornResource);
        int[] result = new int[resources.size()];
        int index = 0;
        for (int resource : resources) result[index++] = resource;
        return result;
    }

    @Override
    public String toString() { return displayName; }
}
