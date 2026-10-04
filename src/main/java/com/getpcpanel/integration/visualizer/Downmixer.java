package com.getpcpanel.integration.visualizer;

/**
 * Turns interleaved multi-channel audio into mono at a lower rate: the average of every channel over {@code factor}
 * frames becomes one sample. A group split across two calls is finished in the next. Allocates nothing.
 */
public final class Downmixer {
    /** The rate the visualizer analyses at, roughly. */
    static final double TARGET_RATE = 22_050;

    private final int channels;
    private final int factor;
    private final float scale;
    private float partial;
    private int partialFrames;

    public Downmixer(int channels, int factor) {
        this.channels = channels;
        this.factor = factor;
        scale = 1f / (channels * factor);
    }

    /** The decimation factor that brings {@code mixRate} closest to about 22 kHz, at least 1. */
    public static int factorFor(int mixRate) {
        return (int) Math.max(1, Math.round(mixRate / TARGET_RATE));
    }

    /** Reads {@code frames} frames of {@code interleaved}, writes mono samples into {@code out} from {@code outPos}. */
    public int process(float[] interleaved, int frames, float[] out, int outPos) {
        var written = 0;
        var sum = partial;
        var n = partialFrames;
        var i = 0;
        for (var f = 0; f < frames; f++) {
            for (var c = 0; c < channels; c++) {
                sum += interleaved[i++];
            }
            if (++n == factor) {
                out[outPos + written++] = sum * scale;
                sum = 0;
                n = 0;
            }
        }
        partial = sum;
        partialFrames = n;
        return written;
    }
}
