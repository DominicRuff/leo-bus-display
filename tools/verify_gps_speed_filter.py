#!/usr/bin/env python3
"""Compile and exercise the real pure-Java GpsSpeedFilter implementation."""

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/leosprojects/busdisplay/GpsSpeedFilter.java"
MOTION_SOURCE = ROOT / "app/src/main/java/com/leosprojects/busdisplay/EngineMotionState.java"
HARNESS = r"""
import com.leosprojects.busdisplay.GpsSpeedFilter;
import com.leosprojects.busdisplay.EngineMotionState;

public final class GpsSpeedFilterHarness {
    private static void near(float actual, float expected, float tolerance, String name) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        GpsSpeedFilter filter = new GpsSpeedFilter();
        near(filter.getSmoothingAlpha(), 0.80f, 0.0001f, "default alpha");

        float alpha80Fourth = 0f;
        for (int index = 0; index < 4; index++) alpha80Fourth = filter.update(50f);
        if (alpha80Fourth < 45f) {
            throw new AssertionError("alpha 0.80 must reach 45 km/h within four samples");
        }

        filter = new GpsSpeedFilter(1f);
        float fast = filter.update(50f);
        near(fast, 50f, 0.0001f, "alpha 1.00 response");
        filter = new GpsSpeedFilter(0.35f);
        float slow = filter.update(50f);
        if (!(slow < fast)) throw new AssertionError("alpha 0.35 must be slower than 1.00");
        filter.setSmoothingAlpha(5f);
        near(filter.getSmoothingAlpha(), 1f, 0.0001f, "upper alpha clamp");
        filter.setSmoothingAlpha(-1f);
        near(filter.getSmoothingAlpha(), 0.20f, 0.0001f, "lower alpha clamp");
        filter.setSmoothingAlpha(Float.NaN);
        near(filter.getSmoothingAlpha(), 0.80f, 0.0001f, "invalid alpha fallback");

        filter = new GpsSpeedFilter();
        near(filter.update(0f), 0f, 0.0001f, "zero");
        filter.reset();
        near(filter.update(1f), 0f, 0.0001f, "raw stationary clamp");
        near(filter.update(1f), 0f, 0.0001f, "repeated raw stationary clamp");
        near(filter.update(1f), 0f, 0.0001f, "stable raw stationary clamp");

        filter.reset();
        filter.update(3f);
        filter.update(3f);
        float lowMoving = filter.update(3f);
        if (!(lowMoving > 0f && lowMoving < 3f)) {
            throw new AssertionError("repeated 3 km/h must leave zero and converge toward 3");
        }

        filter.reset();
        filter.update(2.5f);
        filter.update(2.5f);
        if (filter.update(2.5f) <= 0f) {
            throw new AssertionError("repeated 2.5 km/h must eventually become non-zero");
        }

        boolean settledAtZero = false;
        for (int index = 0; index < 20; index++) {
            if (filter.update(0f) == 0f) settledAtZero = true;
        }
        if (!settledAtZero) {
            throw new AssertionError("zero samples after movement must settle to zero output");
        }

        filter.reset();
        float first = filter.update(30f);
        float second = filter.update(30f);
        float third = filter.update(30f);
        if (!(first > 0f && first < second && second < third && third < 30f)) {
            throw new AssertionError("stable samples must converge smoothly toward 30");
        }

        filter.reset();
        filter.update(30f);
        filter.update(30f);
        float withOutlier = filter.update(300f);
        filter.reset();
        filter.update(30f);
        filter.update(30f);
        float withoutOutlier = filter.update(30f);
        near(withOutlier, withoutOutlier, 0.0001f, "three-sample median");

        filter.update(80f);
        filter.reset();
        near(filter.update(10f), 8f, 0.0001f, "reset smoothing history");
        filter.reset();
        if (filter.update(-20f) < 0f) throw new AssertionError("negative output");

        EngineMotionState motion = new EngineMotionState();
        motion.reset(0);
        if (motion.state() != EngineMotionState.State.STOPPED
                || motion.onSpeedKmh(0) != EngineMotionState.Action.NONE) {
            throw new AssertionError("startup at zero must remain STOPPED");
        }
        if (motion.continuousLoopBand(1, false) != -1) {
            throw new AssertionError("STOPPED at zero must not select a moving loop");
        }
        motion.reset(2);
        if (motion.state() != EngineMotionState.State.STOPPED
                || motion.continuousLoopBand(1, false) != -1) {
            throw new AssertionError("STOPPED at 2 km/h must not select Gear 1");
        }
        if (motion.continuousLoopBand(1, true) != 0) {
            throw new AssertionError("STOPPED with an idle loop must select idle");
        }
        if (motion.onSpeedKmh(3) != EngineMotionState.Action.START_MOVING
                || motion.continuousLoopBand(1, false) != 1) {
            throw new AssertionError("3 km/h must permit the moving Gear 1 loop");
        }
        if (motion.onSpeedKmh(0) != EngineMotionState.Action.START_STOPPING
                || motion.continuousLoopBand(1, true) != -1) {
            throw new AssertionError("moving to zero must start STOPPING once");
        }
        if (motion.onSpeedKmh(0) != EngineMotionState.Action.NONE) {
            throw new AssertionError("repeated zero must not replay STOPPING");
        }
        motion.completeStopping();
        if (motion.state() != EngineMotionState.State.STOPPED) {
            throw new AssertionError("stopping duration must complete as STOPPED");
        }
        if (motion.onSpeedKmh(2) != EngineMotionState.Action.NONE
                || motion.onSpeedKmh(0) != EngineMotionState.Action.NONE) {
            throw new AssertionError("0..2 km/h jitter must not re-arm stopping");
        }
        if (motion.onSpeedKmh(3) != EngineMotionState.Action.START_MOVING) {
            throw new AssertionError("3 km/h must re-arm movement");
        }
        motion.onSpeedKmh(0);
        if (motion.onSpeedKmh(3) != EngineMotionState.Action.START_MOVING) {
            throw new AssertionError("movement during STOPPING must cancel it");
        }
        motion.reset(0);
        if (motion.state() != EngineMotionState.State.STOPPED) {
            throw new AssertionError("engine/profile reset must clear stopping state");
        }
        System.out.println("OK: real GPS/motion Java harness; alpha80 sample4="
                + alpha80Fourth + " km/h");
    }
}
"""

if not shutil.which("javac") or not shutil.which("java"):
    raise SystemExit("ERROR: javac and java are required for this verifier")

with tempfile.TemporaryDirectory(prefix="leo-gps-filter-") as temporary:
    directory = Path(temporary)
    harness = directory / "GpsSpeedFilterHarness.java"
    harness.write_text(HARNESS, encoding="utf-8")
    subprocess.run(["javac", "-d", str(directory), str(SOURCE), str(MOTION_SOURCE),
                    str(harness)], check=True)
    subprocess.run(["java", "-cp", str(directory), "GpsSpeedFilterHarness"], check=True)
