package com.leosprojects.busdisplay;

import java.util.Collections;
import java.util.List;

public final class BusSoundPack {
    public static final BusSoundPack PROTOTYPE_DIESEL_TEST = new BusSoundPack(
            "prototype_diesel_test",
            "Prototype Diesel Test",
            R.raw.prototype_engine_start,
            R.raw.prototype_engine_loop,
            R.raw.prototype_gear_shift,
            R.raw.prototype_horn,
            850
    );

    private static final List<BusSoundPack> PACKS =
            Collections.singletonList(PROTOTYPE_DIESEL_TEST);

    private final String id;
    private final String displayName;
    private final int engineStartResource;
    private final int engineLoopResource;
    private final int gearChangeResource;
    private final int hornResource;
    private final long engineStartDurationMs;

    public BusSoundPack(String id, String displayName, int engineStartResource,
                        int engineLoopResource, int gearChangeResource, int hornResource,
                        long engineStartDurationMs) {
        this.id = id;
        this.displayName = displayName;
        this.engineStartResource = engineStartResource;
        this.engineLoopResource = engineLoopResource;
        this.gearChangeResource = gearChangeResource;
        this.hornResource = hornResource;
        this.engineStartDurationMs = engineStartDurationMs;
    }

    public static List<BusSoundPack> packs() {
        return PACKS;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int engineStartResource() { return engineStartResource; }
    public int engineLoopResource() { return engineLoopResource; }
    public int gearChangeResource() { return gearChangeResource; }
    public int hornResource() { return hornResource; }
    public long engineStartDurationMs() { return engineStartDurationMs; }

    @Override
    public String toString() {
        return displayName;
    }
}
