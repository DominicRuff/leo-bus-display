#!/usr/bin/env python3
"""Compile and exercise the real pure-Java GpsSpeedFilter implementation."""

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/leosprojects/busdisplay/GpsSpeedFilter.java"
HARNESS = r"""
import com.leosprojects.busdisplay.GpsSpeedFilter;

public final class GpsSpeedFilterHarness {
    private static void near(float actual, float expected, float tolerance, String name) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        GpsSpeedFilter filter = new GpsSpeedFilter();
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
        near(filter.update(10f), 3.5f, 0.0001f, "reset smoothing history");
        filter.reset();
        if (filter.update(-20f) < 0f) throw new AssertionError("negative output");
        System.out.println("OK: real GpsSpeedFilter Java harness");
    }
}
"""

if not shutil.which("javac") or not shutil.which("java"):
    raise SystemExit("ERROR: javac and java are required for this verifier")

with tempfile.TemporaryDirectory(prefix="leo-gps-filter-") as temporary:
    directory = Path(temporary)
    harness = directory / "GpsSpeedFilterHarness.java"
    harness.write_text(HARNESS, encoding="utf-8")
    subprocess.run(["javac", "-d", str(directory), str(SOURCE), str(harness)], check=True)
    subprocess.run(["java", "-cp", str(directory), "GpsSpeedFilterHarness"], check=True)
