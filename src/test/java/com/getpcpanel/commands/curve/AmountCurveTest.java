package com.getpcpanel.commands.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Amount family spans linear (0) through the historic logarithmic taper (+50) and beyond, mirrored
 * for negative amounts. Amount +50 is the curve every profile that predates named curves resolves to,
 * so its shape is pinned against the original exponential here.
 */
class AmountCurveTest {
    /** The historic hardcoded taper: {@code (base^(100x) - 1) / 100}, unrounded. */
    private static double legacyLog(double x) {
        return (Math.pow(1.04723275, 100 * x) - 1) / 100;
    }

    @Test
    void amountZeroIsIdentity() {
        var curve = new AmountCurve(0);
        for (var i = 0; i <= 100; i++) {
            var x = i / 100d;
            assertEquals(x, curve.apply(x), 1e-12, "at x=" + x);
        }
    }

    @Test
    void everyAmountSpansTheFullRange() {
        for (var amount = -100; amount <= 100; amount += 5) {
            var curve = new AmountCurve(amount);
            assertEquals(0d, curve.apply(0), 1e-12, "start of amount " + amount);
            assertEquals(1d, curve.apply(1), 1e-12, "end of amount " + amount);
        }
    }

    @Test
    void amountFiftyReproducesTheHistoricLogarithmicTaper() {
        var curve = new AmountCurve(50);
        for (var i = 0; i <= 255; i++) {
            var x = i / 255d;
            assertEquals(legacyLog(x), curve.apply(x), 1e-5, "at x=" + x);
        }
    }

    @Test
    void positiveAmountsGiveFinerControlAtTheBottom() {
        var curve = new AmountCurve(50);
        for (var i = 1; i < 100; i++) {
            var x = i / 100d;
            assertTrue(curve.apply(x) < x, "expected below the diagonal at x=" + x);
        }
    }

    @Test
    void negativeAmountMirrorsThePositiveCurve() {
        var positive = new AmountCurve(35);
        var negative = new AmountCurve(-35);
        for (var i = 0; i <= 100; i++) {
            var x = i / 100d;
            assertEquals(1 - positive.apply(1 - x), negative.apply(x), 1e-12, "at x=" + x);
        }
    }

    @Test
    void everyAmountIsMonotonic() {
        for (var amount = -100; amount <= 100; amount += 5) {
            var curve = new AmountCurve(amount);
            var previous = -1d;
            for (var i = 0; i <= 255; i++) {
                var current = curve.apply(i / 255d);
                assertTrue(current >= previous, "amount " + amount + " dipped at x=" + (i / 255d));
                previous = current;
            }
        }
    }

    @Test
    void inputOutsideTheUnitRangeIsClamped() {
        var curve = new AmountCurve(50);
        assertEquals(0d, curve.apply(-0.5), 1e-12);
        assertEquals(1d, curve.apply(1.5), 1e-12);
    }
}
