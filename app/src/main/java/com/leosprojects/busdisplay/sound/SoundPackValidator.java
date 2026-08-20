package com.leosprojects.busdisplay.sound;

import android.content.Context;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SoundPackValidator {
    public static final class Result {
        public final boolean valid;
        public final List<String> lines;

        Result(boolean valid, List<String> lines) {
            this.valid = valid;
            this.lines = Collections.unmodifiableList(lines);
        }

        public String summary() {
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                if (result.length() > 0) result.append('\n');
                result.append(line);
            }
            return result.toString();
        }
    }

    private final Context context;
    public SoundPackValidator(Context context) { this.context = context.getApplicationContext(); }

    public Result validate(SoundPack pack) {
        List<String> lines = new ArrayList<>();
        boolean valid = checkRequired("Idle", pack.idle == null ? null : pack.idle.assetPath, lines);
        if (pack.gears.isEmpty()) {
            lines.add("✗ No running gear sounds");
            valid = false;
        }
        for (SoundPack.Gear gear : pack.gears) {
            valid &= checkRequired("Gear " + gear.number, gear.assetPath, lines);
        }
        checkOptional("Engine start", pack.engineStartAsset, lines);
        checkOptional("Downshift", pack.downshiftAsset, lines);
        checkOptional("Brake", pack.brakeAsset, lines);
        checkOptional("Horn", pack.hornAsset, lines);
        checkOptional("Doors open", pack.doorsOpenAsset, lines);
        checkOptional("Doors close", pack.doorsCloseAsset, lines);
        return new Result(valid, lines);
    }

    private boolean checkRequired(String label, String path, List<String> lines) {
        boolean available = exists(path);
        lines.add((available ? "✓ " : "✗ ") + label);
        return available;
    }

    private void checkOptional(String label, String path, List<String> lines) {
        lines.add((exists(path) ? "✓ " : "○ ") + label
                + (path == null ? " unavailable" : ""));
    }

    private boolean exists(String path) {
        if (path == null) return false;
        try (InputStream ignored = context.getAssets().open(path)) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
