package com.getpcpanel.commands.curve;

/**
 * A resolved response curve: maps a normalised dial position (0..1) to a normalised output (0..1).
 *
 * <p>This is the shape only. Trim, move start/end and invert are applied around it by
 * {@link com.getpcpanel.commands.DialValueCalculator}, so a curve never needs to know the target range.
 */
@FunctionalInterface
public interface Curve {
    /** Straight through — the curve every control uses unless it names another one. */
    Curve LINEAR = x -> Math.clamp(x, 0d, 1d);

    double apply(double x);
}
