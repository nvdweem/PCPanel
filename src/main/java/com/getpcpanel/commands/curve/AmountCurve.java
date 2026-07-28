package com.getpcpanel.commands.curve;

/**
 * The one-knob curve family: {@code amount} slides the taper from straight through (0) to the historic
 * logarithmic shape ({@link #LOG_AMOUNT}) and on to steeper curves, with negative amounts mirroring it so
 * the fine control sits at the top of the throw instead of the bottom.
 *
 * <p>The exponential is normalised by its own value at x=1, so every amount starts at 0 and ends at 1.
 */
public record AmountCurve(int amount) implements Curve {
    /** The base of the historic taper, which mapped 0-100 onto 1-101 exponentially. */
    private static final double LOG_BASE = 1.04723275;

    /** The amount at which the family reproduces that historic taper. */
    public static final int LOG_AMOUNT = 50;

    private static final double BASE_PER_AMOUNT = (LOG_BASE - 1) / LOG_AMOUNT;

    @Override
    public double apply(double x) {
        var position = Math.clamp(x, 0d, 1d);
        var magnitude = Math.abs(amount);
        if (magnitude == 0) {
            return position;
        }
        return amount > 0 ? exponential(position, magnitude) : 1 - exponential(1 - position, magnitude);
    }

    private static double exponential(double x, int magnitude) {
        var base = 1 + magnitude * BASE_PER_AMOUNT;
        return (Math.pow(base, 100 * x) - 1) / (Math.pow(base, 100) - 1);
    }
}
