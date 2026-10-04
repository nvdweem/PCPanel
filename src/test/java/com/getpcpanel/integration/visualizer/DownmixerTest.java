package com.getpcpanel.integration.visualizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DownmixerTest {
    @Test
    void monoWithoutDecimationPassesThrough() {
        var out = new float[3];
        assertEquals(3, new Downmixer(1, 1).process(new float[] { 0.1f, -0.2f, 0.3f }, 3, out, 0));
        assertArrayEquals(new float[] { 0.1f, -0.2f, 0.3f }, out, 1e-6f);
    }

    @Test
    void averagesChannelsAndSamples() {
        var out = new float[2];
        // stereo, factor 2: frames (1,3) (5,7) -> 4 ; (0,0) (2,2) -> 1
        assertEquals(2, new Downmixer(2, 2).process(new float[] { 1, 3, 5, 7, 0, 0, 2, 2 }, 4, out, 0));
        assertArrayEquals(new float[] { 4, 1 }, out, 1e-6f);
    }

    @Test
    void carriesAPartialAverageIntoTheNextCall() {
        var downmixer = new Downmixer(1, 2);
        var out = new float[4];
        assertEquals(1, downmixer.process(new float[] { 1, 3, 5 }, 3, out, 0)); // 2, and 5 waits
        assertEquals(1, downmixer.process(new float[] { 7 }, 1, out, 1)); // (5+7)/2
        assertArrayEquals(new float[] { 2, 6, 0, 0 }, out, 1e-6f);
    }

    @Test
    void picksTheFactorForAbout22kHz() {
        assertEquals(2, Downmixer.factorFor(48_000));
        assertEquals(2, Downmixer.factorFor(44_100));
        assertEquals(4, Downmixer.factorFor(96_000));
        assertEquals(1, Downmixer.factorFor(22_050));
        assertEquals(1, Downmixer.factorFor(16_000));
    }
}
