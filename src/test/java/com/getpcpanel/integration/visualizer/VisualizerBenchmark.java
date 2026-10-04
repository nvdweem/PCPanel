package com.getpcpanel.integration.visualizer;

import java.util.Random;

/**
 * Times the visualizer's per-frame work on 60 s of synthetic music, as Windows will run it: 48 kHz stereo, downmixed
 * and halved to 24 kHz mono, then analysed, 20 frames a second. Run it on the JVM and as a native image built with the
 * app's flags (-Os, 64 MB heap) to see what one frame costs. Not a test.
 */
public final class VisualizerBenchmark {
    private static final int MIX_RATE = 48_000;
    private static final int CHANNELS = 2;
    private static final int FPS = 20;
    private static final int SECONDS = 60;
    private static final int PASSES = 10;

    private VisualizerBenchmark() {
    }

    public static void main(String[] args) {
        var factor = Downmixer.factorFor(MIX_RATE);
        var rate = MIX_RATE / factor;
        var framesPerChunk = MIX_RATE / FPS;
        var chunks = SECONDS * FPS;
        var audio = music(chunks * framesPerChunk);
        var mono = new float[framesPerChunk / factor + 1];

        // Warm-up (matters on the JVM; harmless native)
        run(audio, chunks, framesPerChunk, factor, rate, mono, 3);
        var t0 = System.nanoTime();
        var beats = run(audio, chunks, framesPerChunk, factor, rate, mono, PASSES);
        var total = System.nanoTime() - t0;

        var downmixOnly = timeDownmix(audio, chunks, framesPerChunk, factor, mono);
        var frames = (long) chunks * PASSES;
        var perFrameUs = total / 1000.0 / frames;
        var downmixUs = downmixOnly / 1000.0 / frames;
        System.out.printf("runtime: %s%n", System.getProperty("org.graalvm.nativeimage.imagecode") != null ? "native image" : "JVM " + Runtime.version());
        System.out.printf("frames: %d (%d passes of %d s at %d fps), beats seen: %d%n", frames, PASSES, SECONDS, FPS, beats);
        System.out.printf("per frame: %.1f us total (downmix %.1f us, analysis %.1f us)%n", perFrameUs, downmixUs, perFrameUs - downmixUs);
        System.out.printf("CPU at %d fps: %.3f ms per second = %.3f %% of one core%n", FPS, perFrameUs * FPS / 1000.0, perFrameUs * FPS / 10_000.0);
    }

    private static long run(float[] audio, int chunks, int framesPerChunk, int factor, int rate, float[] mono, int passes) {
        long beats = 0;
        for (var p = 0; p < passes; p++) {
            var downmixer = new Downmixer(CHANNELS, factor);
            var analyzer = new BandAnalyzer(rate);
            var chunk = new float[framesPerChunk * CHANNELS];
            for (var c = 0; c < chunks; c++) {
                System.arraycopy(audio, c * framesPerChunk * CHANNELS, chunk, 0, chunk.length); // stands in for reading the capture buffer
                var n = downmixer.process(chunk, framesPerChunk, mono, 0);
                if (analyzer.analyze(mono, n).beat()) {
                    beats++;
                }
            }
        }
        return beats;
    }

    private static long timeDownmix(float[] audio, int chunks, int framesPerChunk, int factor, float[] mono) {
        var t0 = System.nanoTime();
        for (var p = 0; p < PASSES; p++) {
            var downmixer = new Downmixer(CHANNELS, factor);
            var chunk = new float[framesPerChunk * CHANNELS];
            for (var c = 0; c < chunks; c++) {
                System.arraycopy(audio, c * framesPerChunk * CHANNELS, chunk, 0, chunk.length);
                downmixer.process(chunk, framesPerChunk, mono, 0);
            }
        }
        return System.nanoTime() - t0;
    }

    /** Stereo 48 kHz: a 120 BPM kick, a chord, hi-hat noise bursts and a little background noise. */
    private static float[] music(int frames) {
        var random = new Random(42);
        var out = new float[frames * CHANNELS];
        var beat = MIX_RATE / 2;
        for (var i = 0; i < frames; i++) {
            var t = (double) i / MIX_RATE;
            var inBeat = i % beat;
            var kick = inBeat < MIX_RATE / 10 ? 0.6 * Math.sin(2 * Math.PI * 60 * t) * (1 - inBeat / (MIX_RATE / 10.0)) : 0;
            var chord = 0.08 * (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 277 * t) + Math.sin(2 * Math.PI * 330 * t));
            var hat = (i + beat / 2) % (beat / 2) < MIX_RATE / 50 ? 0.15 * (random.nextDouble() * 2 - 1) : 0;
            var noise = 0.01 * (random.nextDouble() * 2 - 1);
            var s = (float) (kick + chord + hat + noise);
            out[i * 2] = s;
            out[i * 2 + 1] = s * 0.9f;
        }
        return out;
    }
}
