package dev.niels.pulse;

/** A sample format ({@code pa_sample_spec}): the {@code pa_sample_format_t} code, channel count and rate. */
public record SampleSpec(int format, int channels, long rate) {
}
