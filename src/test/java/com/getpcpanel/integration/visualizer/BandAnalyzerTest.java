package com.getpcpanel.integration.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BandAnalyzerTest {
    private static final int RATE = 22050;
    private static final int FRAME = RATE / 20;

    @ParameterizedTest
    @CsvSource({ "60, 0", "350, 1", "1500, 2", "6000, 3" })
    void aToneIsLoudestInItsOwnBand(double hz, int band) {
        var analyzer = new BandAnalyzer(RATE);
        var block = new float[FRAME];
        for (var f = 0; f < 20; f++) {
            TestSignals.clear(block);
            TestSignals.addSine(block, FRAME, hz, 0.5, RATE, (long) f * FRAME);
            analyzer.analyze(block, FRAME);
        }
        for (var other = 0; other < BandAnalyzer.BANDS; other++) {
            if (other != band) {
                assertTrue(analyzer.bandRms(band) > analyzer.bandRms(other),
                        hz + " Hz: band " + band + " (" + analyzer.bandRms(band) + ") should beat band " + other + " (" + analyzer.bandRms(other) + ")");
            }
        }
    }

    @Test
    void louderThanUsualLightsUpAndSteadySitsInTheMiddle() {
        var analyzer = new BandAnalyzer(RATE);
        var random = new Random(1);
        var block = new float[FRAME];
        var steady = new double[BandAnalyzer.BANDS];
        BandAnalyzer.Frame frame = null;
        for (var f = 0; f < 100; f++) {
            TestSignals.clear(block);
            TestSignals.addNoise(block, FRAME, 0.05, random);
            frame = analyzer.analyze(block, FRAME);
            for (var b = 0; f >= 50 && b < BandAnalyzer.BANDS; b++) {
                steady[b] += frame.band(b) / 50;
            }
        }
        // Noise's bands wobble from frame to frame (the bass most), so judge the average of a steady stretch.
        for (var b = 0; b < BandAnalyzer.BANDS; b++) {
            assertTrue(steady[b] > 0.15 && steady[b] < 0.6, "steady band " + b + " averages " + steady[b]);
        }
        TestSignals.clear(block);
        TestSignals.addNoise(block, FRAME, 0.4, random);
        frame = analyzer.analyze(block, FRAME);
        for (var b = 0; b < BandAnalyzer.BANDS; b++) {
            assertTrue(frame.band(b) > 0.9, "louder band " + b + " = " + frame.band(b));
        }
        assertTrue(frame.overall() > 0.9, "louder overall = " + frame.overall());
    }

    @Test
    void silenceFadesEverythingOut() {
        var analyzer = new BandAnalyzer(RATE);
        var random = new Random(2);
        var block = new float[FRAME];
        for (var f = 0; f < 20; f++) {
            TestSignals.clear(block);
            TestSignals.addNoise(block, FRAME, 0.3, random);
            analyzer.analyze(block, FRAME);
        }
        TestSignals.clear(block);
        BandAnalyzer.Frame frame = null;
        for (var f = 0; f < 10; f++) {
            frame = analyzer.analyze(block, f % 2 == 0 ? FRAME : 0); // zeros and empty reads are both silence
        }
        for (var b = 0; b < BandAnalyzer.BANDS; b++) {
            assertEquals(0f, frame.band(b), 0.01f, "band " + b);
        }
        assertEquals(0f, frame.overall(), 0.01f);
        assertFalse(frame.beat());
    }

    @Test
    void kicksAreBeats() {
        var analyzer = new BandAnalyzer(RATE);
        var beats = beatFrames(analyzer, 10, 200, new Random(3)); // a kick every 10 frames (120 BPM), 10 s
        assertTrue(beats.size() >= 15 && beats.size() <= 20, "beats: " + beats);
    }

    @Test
    void beatsAreNeverCloserThanTheMinimum() {
        var analyzer = new BandAnalyzer(RATE);
        var beats = beatFrames(analyzer, 2, 200, new Random(4)); // a kick every other frame
        assertFalse(beats.isEmpty());
        for (var i = 1; i < beats.size(); i++) {
            assertTrue(beats.get(i) - beats.get(i - 1) >= BandAnalyzer.BEAT_MIN_FRAMES, "beats at " + beats);
        }
    }

    @Test
    void analyzingAllocatesNothing() {
        var analyzer = new BandAnalyzer(RATE);
        var block = new float[FRAME];
        TestSignals.addNoise(block, FRAME, 0.3, new Random(5));
        for (var f = 0; f < 2_000; f++) { // warm up so the JIT has compiled the loop
            analyzer.analyze(block, FRAME);
        }
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var before = threads.getCurrentThreadAllocatedBytes();
        for (var f = 0; f < 1_000; f++) {
            analyzer.analyze(block, FRAME);
        }
        var allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1024, "allocated " + allocated + " bytes over 1000 frames");
    }

    /** Frames that reported a beat, for a one-frame 60 Hz kick every {@code every} frames over a quiet mix. */
    private static List<Integer> beatFrames(BandAnalyzer analyzer, int every, int frames, Random random) {
        var block = new float[FRAME];
        var beats = new ArrayList<Integer>();
        for (var f = 0; f < frames; f++) {
            TestSignals.clear(block);
            TestSignals.addNoise(block, FRAME, 0.03, random);
            TestSignals.addSine(block, FRAME, 440, 0.05, RATE, (long) f * FRAME);
            if (f % every == 0) {
                TestSignals.addSine(block, FRAME, 60, 0.8, RATE, (long) f * FRAME);
            }
            if (analyzer.analyze(block, FRAME).beat()) {
                beats.add(f);
            }
        }
        return beats;
    }
}
