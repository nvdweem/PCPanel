package com.getpcpanel.integration.visualizer;

/**
 * Splits mono audio into four bands for the music visualizer: bass (below 150 Hz), low mids (150-600), high mids
 * (600-3000) and treble (above 3000), from three one-pole low-passes. Call {@link #analyze} once per frame (about
 * 50 ms of audio); it allocates nothing and returns the same {@link Frame} every time.
 *
 * <p>Each band is shown relative to its own running average, so quiet and loud songs both move the lights: a band
 * 3 dB under its average is dark, 4.5 dB over is full, then an S-curve adds contrast. A band more than 30 dB under
 * the whole mix is dark. Levels rise at once and fall quickly. A beat is the bass going over 1.5x its average while
 * its band is above 0.6, at most once every {@link #BEAT_MIN_FRAMES} frames.
 *
 * <p>Needs about 22 kHz or more: at lower rates a one-pole filter at 3000 Hz cannot separate the treble.
 * Not thread-safe.
 */
public final class BandAnalyzer {
    public static final int BANDS = 4;
    static final int BEAT_MIN_FRAMES = 6;
    private static final double[] CROSSOVERS_HZ = { 150, 600, 3000 };
    /** Below this the mix counts as silence: levels fall and the averages hold. */
    private static final double SILENCE_DB = -80;
    private static final double GATE_DB = 30;
    private static final double QUIET_DB = -3;
    private static final double LOUD_DB = 4.5;
    /** How far an average moves towards the current frame, per frame: about a second at 20 fps. */
    private static final double AVERAGE_RATE = 0.05;
    /** What is left of a level one frame later when nothing holds it up. */
    private static final float FALL = 0.6f;
    private static final double BEAT_RATIO = 1.5;
    private static final float BEAT_MIN_BASS = 0.6f;

    private final float a0;
    private final float a1;
    private final float a2;
    private float lp0;
    private float lp1;
    private float lp2;
    private final double[] rms = new double[BANDS];
    private final double[] averageDb = new double[BANDS];
    private double overallAverageDb;
    private double bassRmsAverage;
    private boolean primed;
    private int framesSinceBeat = BEAT_MIN_FRAMES;
    private final Frame frame = new Frame();

    public BandAnalyzer(int sampleRate) {
        a0 = coefficient(CROSSOVERS_HZ[0], sampleRate);
        a1 = coefficient(CROSSOVERS_HZ[1], sampleRate);
        a2 = coefficient(CROSSOVERS_HZ[2], sampleRate);
    }

    private static float coefficient(double hz, int sampleRate) {
        return (float) (1 - Math.exp(-2 * Math.PI * hz / sampleRate));
    }

    /** Analyses {@code count} samples (0 is a silent frame) and returns the reused frame. */
    public Frame analyze(float[] samples, int count) {
        double s0 = 0;
        double s1 = 0;
        double s2 = 0;
        double s3 = 0;
        double all = 0;
        var l0 = lp0;
        var l1 = lp1;
        var l2 = lp2;
        for (var i = 0; i < count; i++) {
            var x = samples[i];
            l0 += a0 * (x - l0);
            l1 += a1 * (x - l1);
            l2 += a2 * (x - l2);
            var b1 = l1 - l0;
            var b2 = l2 - l1;
            var b3 = x - l2;
            s0 += l0 * l0;
            s1 += b1 * b1;
            s2 += b2 * b2;
            s3 += b3 * b3;
            all += x * x;
        }
        lp0 = l0;
        lp1 = l1;
        lp2 = l2;
        framesSinceBeat++;
        frame.beat = false;

        var overallDb = count == 0 ? Double.NEGATIVE_INFINITY : db(Math.sqrt(all / count));
        frame.silent = overallDb < SILENCE_DB;
        if (frame.silent) {
            for (var b = 0; b < BANDS; b++) {
                rms[b] = 0;
                frame.bands[b] *= FALL;
            }
            frame.overall *= FALL;
            return frame;
        }
        rms[0] = Math.sqrt(s0 / count);
        rms[1] = Math.sqrt(s1 / count);
        rms[2] = Math.sqrt(s2 / count);
        rms[3] = Math.sqrt(s3 / count);
        if (!primed) {
            for (var b = 0; b < BANDS; b++) {
                averageDb[b] = db(rms[b]);
            }
            overallAverageDb = overallDb;
            bassRmsAverage = rms[0];
            primed = true;
        }
        for (var b = 0; b < BANDS; b++) {
            var bandDb = db(rms[b]);
            var level = bandDb < overallDb - GATE_DB ? 0 : relative(bandDb - averageDb[b]);
            frame.bands[b] = Math.max(level, frame.bands[b] * FALL);
            averageDb[b] += AVERAGE_RATE * (bandDb - averageDb[b]);
        }
        frame.overall = Math.max(relative(overallDb - overallAverageDb), frame.overall * FALL);
        overallAverageDb += AVERAGE_RATE * (overallDb - overallAverageDb);

        if (rms[0] > BEAT_RATIO * bassRmsAverage && frame.bands[0] > BEAT_MIN_BASS && framesSinceBeat >= BEAT_MIN_FRAMES) {
            frame.beat = true;
            framesSinceBeat = 0;
        }
        bassRmsAverage += AVERAGE_RATE * (rms[0] - bassRmsAverage);
        return frame;
    }

    /** A level 0..1 from how far (dB) a band is above its average, through an S-curve. */
    private static float relative(double overAverageDb) {
        var x = Math.clamp((overAverageDb - QUIET_DB) / (LOUD_DB - QUIET_DB), 0.0, 1.0);
        return (float) (x * x * (3 - 2 * x));
    }

    private static double db(double rms) {
        return 20 * Math.log10(Math.max(rms, 1e-9));
    }

    /** The last frame's RMS of a band, before any scaling. */
    double bandRms(int band) {
        return rms[band];
    }

    /** One frame's result. Read it before the next {@link #analyze}: the same instance is filled every time. */
    public static final class Frame {
        private final float[] bands = new float[BANDS];
        private float overall;
        private boolean beat;
        private boolean silent;

        /** 0..1 for bass, low mids, high mids, treble. */
        public float band(int band) {
            return bands[band];
        }

        public float overall() {
            return overall;
        }

        public boolean beat() {
            return beat;
        }

        /** Nothing audible in this frame (below -80 dBFS, or no samples). */
        public boolean silent() {
            return silent;
        }
    }
}
