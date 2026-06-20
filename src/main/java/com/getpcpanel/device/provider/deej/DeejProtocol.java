package com.getpcpanel.device.provider.deej;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Pure, hardware-free implementation of the Deej serial protocol (device -&gt; host only). Deej
 * firmware writes one ASCII line per sample: pipe-delimited integers terminated by CRLF, e.g.
 * {@code 0|240|1023|0|483\r\n}. Each value is a raw 10-bit ADC reading (0-1023); the number of
 * values per line is the slider count and is dynamic (learned from the first valid line).
 *
 * <p>All methods are static and side-effect free so the protocol can be unit-tested by feeding
 * canned lines, with no serial port or hardware.
 */
public final class DeejProtocol {
    /** Max raw value of a Deej 10-bit ADC reading. */
    public static final int RAW_MAX = 1023;
    /** Canonical internal analog domain max (shared with PCPanel). */
    public static final int NORMALIZED_MAX = 255;

    // Deej validates each line against this before splitting on '|'. Trailing CR/LF stripped first.
    private static final Pattern LINE = Pattern.compile("^\\d{1,4}(\\|\\d{1,4})*$");

    private DeejProtocol() {
    }

    /**
     * Parses one Deej line into its raw slider values (0-1023). Trailing {@code \r}/{@code \n} are
     * stripped first; the remainder must match {@code ^\d{1,4}(\|\d{1,4})*$}. Returns {@code null}
     * for any invalid/garbage/partial/empty line (so the caller can simply skip it). Out-of-range
     * values (a 4-digit number &gt; 1023) are clamped to 1023.
     */
    @Nullable
    public static int[] parse(@Nullable String line) {
        if (line == null) {
            return null;
        }
        // Strip a single trailing CRLF / LF / CR (firmware uses CRLF, but tolerate bare LF too).
        var trimmed = line;
        while (!trimmed.isEmpty()) {
            var last = trimmed.charAt(trimmed.length() - 1);
            if (last == '\r' || last == '\n') {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            } else {
                break;
            }
        }
        if (trimmed.isEmpty() || !LINE.matcher(trimmed).matches()) {
            return null;
        }
        var parts = trimmed.split("\\|");
        var values = new int[parts.length];
        for (var i = 0; i < parts.length; i++) {
            int raw;
            try {
                raw = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null; // Defensive: the regex already guarantees 1-4 digits.
            }
            values[i] = Math.min(raw, RAW_MAX);
        }
        return values;
    }

    /** Normalizes a raw 0-1023 reading to the canonical 0-255 domain (rounded to nearest). */
    public static int normalize(int raw) {
        var clamped = Math.max(0, Math.min(RAW_MAX, raw));
        return (int) Math.round(clamped * (double) NORMALIZED_MAX / RAW_MAX);
    }

    /**
     * Deej's per-slider noise-reduction levels. The threshold is applied to the 0.0-1.0 normalized
     * value; a change is only emitted when the absolute delta meets the threshold, with rail
     * snapping always emitted (see {@link #significantlyDifferent}).
     */
    public enum NoiseReduction {
        LOW(0.015), DEFAULT(0.025), HIGH(0.035);

        private final double threshold;

        NoiseReduction(double threshold) {
            this.threshold = threshold;
        }

        public double threshold() {
            return threshold;
        }

        public static NoiseReduction fromString(@Nullable String name) {
            if (name == null) {
                return DEFAULT;
            }
            return switch (name.trim().toLowerCase()) {
                case "low" -> LOW;
                case "high" -> HIGH;
                default -> DEFAULT;
            };
        }
    }

    private static final double RAIL_TOLERANCE = 1e-6;

    /**
     * Mirrors deej's {@code SignificantlyDifferent}: returns {@code true} when {@code newVal} differs
     * from {@code oldVal} by at least {@code threshold} on the 0.0-1.0 scale, OR when {@code newVal}
     * snaps to a rail (it is ~1.0 and the old value was not, or it is ~0.0 and the old value was
     * not). This suppresses sub-threshold pot jitter at ~100 Hz while always passing a full-min/max
     * move.
     */
    public static boolean significantlyDifferent(double oldVal, double newVal, double threshold) {
        if (Math.abs(newVal - oldVal) >= threshold) {
            return true;
        }
        // Snap to the rails: always emit when arriving at 1.0 / 0.0 from somewhere else.
        var newIsTop = Math.abs(newVal - 1.0) <= RAIL_TOLERANCE;
        var oldIsTop = Math.abs(oldVal - 1.0) <= RAIL_TOLERANCE;
        if (newIsTop && !oldIsTop) {
            return true;
        }
        var newIsBottom = Math.abs(newVal) <= RAIL_TOLERANCE;
        var oldIsBottom = Math.abs(oldVal) <= RAIL_TOLERANCE;
        return newIsBottom && !oldIsBottom;
    }

    /** Convenience: significance check on raw 0-1023 values for the given level. */
    public static boolean significantlyDifferentRaw(int oldRaw, int newRaw, NoiseReduction level) {
        return significantlyDifferent(oldRaw / (double) RAW_MAX, newRaw / (double) RAW_MAX, level.threshold());
    }
}
