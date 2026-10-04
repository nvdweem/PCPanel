package com.getpcpanel.integration.visualizer;

import java.util.Arrays;
import java.util.Random;

/** Synthetic audio for the visualizer tests and benchmark. */
final class TestSignals {
    private TestSignals() {
    }

    /** Adds a sine of {@code amplitude} at {@code hz}, starting at absolute sample {@code start}, into {@code into}. */
    static void addSine(float[] into, int count, double hz, double amplitude, int sampleRate, long start) {
        for (var i = 0; i < count; i++) {
            into[i] += (float) (amplitude * Math.sin(2 * Math.PI * hz * (start + i) / sampleRate));
        }
    }

    /** Adds uniform noise in [-amplitude, amplitude]. */
    static void addNoise(float[] into, int count, double amplitude, Random random) {
        for (var i = 0; i < count; i++) {
            into[i] += (float) (amplitude * (random.nextDouble() * 2 - 1));
        }
    }

    static void clear(float[] into) {
        Arrays.fill(into, 0f);
    }
}
