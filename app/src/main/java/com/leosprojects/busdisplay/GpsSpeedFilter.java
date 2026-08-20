package com.leosprojects.busdisplay;

import java.util.Arrays;

/** Pure-Java GPS speed filtering with a short median window and gentle smoothing. */
public final class GpsSpeedFilter {
    private static final float RAW_STATIONARY_KMH = 2.0f;
    private static final float FILTERED_STATIONARY_KMH = 1.5f;
    private static final float SMOOTHING_ALPHA = 0.35f;

    private final float[] samples = new float[3];
    private int sampleCount;
    private int nextSample;
    private float filteredKmh;

    public float update(float rawKmh) {
        float sample = Math.max(0f, rawKmh);
        if (sample < RAW_STATIONARY_KMH) sample = 0f;
        samples[nextSample] = sample;
        nextSample = (nextSample + 1) % samples.length;
        if (sampleCount < samples.length) sampleCount++;

        float[] ordered = Arrays.copyOf(samples, sampleCount);
        Arrays.sort(ordered);
        float median = ordered[sampleCount / 2];
        if (sampleCount == 2) median = (ordered[0] + ordered[1]) / 2f;

        filteredKmh += SMOOTHING_ALPHA * (median - filteredKmh);
        return filteredKmh < FILTERED_STATIONARY_KMH ? 0f : filteredKmh;
    }

    public void reset() {
        Arrays.fill(samples, 0f);
        sampleCount = 0;
        nextSample = 0;
        filteredKmh = 0f;
    }
}
