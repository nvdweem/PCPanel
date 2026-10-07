package dev.niels.pulse;

/** A sample format ({@code pa_sample_spec}): the {@code pa_sample_format_t} code, channel count and rate. */
public record SampleSpec(int format, int channels, long rate) {
    /** {@code PA_SAMPLE_FLOAT32LE}. */
    public static final int FLOAT32LE = 5;

    public static SampleSpec float32Mono(int rate) {
        return new SampleSpec(FLOAT32LE, 1, rate);
    }

    /** The size of one sample on every channel, in bytes, for the 32-bit float and 16-bit formats. */
    public int frameSize() {
        return channels * (format == FLOAT32LE ? Float.BYTES : 2);
    }
}
