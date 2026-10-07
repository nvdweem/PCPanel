package dev.niels.pulse;

import java.util.Arrays;

/**
 * A per-channel volume ({@code pa_cvolume}). {@link #NORM} is 100 %; values are on PulseAudio's cubic scale, the same
 * numbers {@code pactl} prints and accepts.
 */
public final class ChannelVolumes {
    public static final long NORM = 0x10000;

    private final long[] values;

    public ChannelVolumes(long... values) {
        this.values = values.clone();
    }

    /** Every one of {@code channels} channels at {@code volume}, which is what {@code pactl set-*-volume} sends. */
    public static ChannelVolumes uniform(int channels, long volume) {
        var values = new long[Math.max(1, channels)];
        Arrays.fill(values, volume);
        return new ChannelVolumes(values);
    }

    public int channels() {
        return values.length;
    }

    public long get(int channel) {
        return values[channel];
    }

    /** The loudest channel, which is how PulseAudio itself reduces a balance to one volume. */
    public long max() {
        return Arrays.stream(values).max().orElse(0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelVolumes other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
