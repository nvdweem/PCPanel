package com.getpcpanel.commands.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.CurvePoint;

/**
 * The hand-drawn curve. Monotone cubic interpolation is what keeps a dragged curve usable: it passes
 * through every point the user placed without the overshoot a plain spline would add between them, which
 * would otherwise make a dial travel backwards between two points the user dragged upwards.
 */
class PointsCurveTest {
    private static PointsCurve curve(double... xy) {
        var points = new ArrayList<CurvePoint>();
        for (var i = 0; i < xy.length; i += 2) {
            points.add(new CurvePoint(xy[i], xy[i + 1]));
        }
        return new PointsCurve(points);
    }

    @Test
    void twoCornerPointsAreAStraightLine() {
        var straight = curve(0, 0, 1, 1);
        for (var i = 0; i <= 100; i++) {
            var x = i / 100d;
            assertEquals(x, straight.apply(x), 1e-9, "at x=" + x);
        }
    }

    @Test
    void passesThroughEveryControlPoint() {
        var points = List.of(new CurvePoint(0, 0), new CurvePoint(0.25, 0.05), new CurvePoint(0.6, 0.4), new CurvePoint(1, 1));
        var shaped = new PointsCurve(points);
        for (var point : points) {
            assertEquals(point.y(), shaped.apply(point.x()), 1e-9, "at x=" + point.x());
        }
    }

    @Test
    void neverOvershootsBetweenPoints() {
        var shaped = curve(0, 0, 0.2, 0.02, 0.5, 0.1, 0.8, 0.45, 1, 1);
        var previous = -1d;
        for (var i = 0; i <= 1000; i++) {
            var value = shaped.apply(i / 1000d);
            assertTrue(value >= previous, "dipped backwards at x=" + (i / 1000d));
            assertTrue(value >= 0 && value <= 1, "left the unit range at x=" + (i / 1000d) + ": " + value);
            previous = value;
        }
    }

    @Test
    void flatSegmentsStayFlat() {
        var shaped = curve(0, 0, 0.4, 0.5, 0.6, 0.5, 1, 1);
        for (var i = 40; i <= 60; i++) {
            assertEquals(0.5, shaped.apply(i / 100d), 1e-9, "at x=" + (i / 100d));
        }
    }

    @Test
    void pointsOutOfOrderAreSorted() {
        var shaped = curve(1, 1, 0.5, 0.2, 0, 0);
        assertEquals(0.2, shaped.apply(0.5), 1e-9);
        assertEquals(0d, shaped.apply(0), 1e-9);
        assertEquals(1d, shaped.apply(1), 1e-9);
    }

    @Test
    void tooFewPointsFallsBackToLinear() {
        assertEquals(0.42, new PointsCurve(List.of()).apply(0.42), 1e-12);
        assertEquals(0.42, new PointsCurve(List.of(new CurvePoint(0, 0))).apply(0.42), 1e-12);
    }

    @Test
    void pointsStackedOnTheSameXCollapseToOne() {
        var shaped = curve(0, 0, 0.5, 0.3, 0.5, 0.31, 1, 1);
        for (var i = 0; i <= 100; i++) {
            assertTrue(Double.isFinite(shaped.apply(i / 100d)), "not finite at x=" + (i / 100d));
        }
        assertEquals(0.3, shaped.apply(0.5), 1e-9);
    }

    @Test
    void inputOutsideTheUnitRangeIsClamped() {
        var shaped = curve(0, 0.1, 1, 0.9);
        assertEquals(0.1, shaped.apply(-2), 1e-9);
        assertEquals(0.9, shaped.apply(2), 1e-9);
    }
}
