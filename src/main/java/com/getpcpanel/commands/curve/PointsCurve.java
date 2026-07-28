package com.getpcpanel.commands.curve;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.getpcpanel.profile.dto.CurvePoint;

/**
 * A hand-drawn curve through arbitrary control points, interpolated with a monotone cubic (Fritsch-Carlson)
 * spline: smooth like a real fader taper, but guaranteed not to overshoot between points — a plain spline
 * would make the dial travel backwards between two points the user dragged upwards.
 *
 * <p>Points are sorted on construction and points sharing an x collapse to the first, so anything the
 * editor can produce evaluates to a finite value.
 */
public final class PointsCurve implements Curve {
    private static final double MIN_SPACING = 1e-9;

    private final double[] xs;
    private final double[] ys;
    private final double[] slopes;

    public PointsCurve(List<CurvePoint> points) {
        var sorted = points.stream().sorted(Comparator.comparingDouble(CurvePoint::x)).toList();
        var xValues = new double[sorted.size()];
        var yValues = new double[sorted.size()];
        var count = 0;
        for (var point : sorted) {
            if (count > 0 && point.x() - xValues[count - 1] < MIN_SPACING) {
                continue;
            }
            xValues[count] = point.x();
            yValues[count] = point.y();
            count++;
        }
        xs = Arrays.copyOf(xValues, count);
        ys = Arrays.copyOf(yValues, count);
        slopes = tangents(xs, ys);
    }

    /** Fritsch-Carlson tangents: zero at every direction change, harmonically weighted elsewhere. */
    private static double[] tangents(double[] xs, double[] ys) {
        var n = xs.length;
        if (n < 2) {
            return new double[n];
        }
        var h = new double[n - 1];
        var delta = new double[n - 1];
        for (var i = 0; i < n - 1; i++) {
            h[i] = xs[i + 1] - xs[i];
            delta[i] = (ys[i + 1] - ys[i]) / h[i];
        }
        var m = new double[n];
        m[0] = delta[0];
        m[n - 1] = delta[n - 2];
        for (var i = 1; i < n - 1; i++) {
            if (delta[i - 1] * delta[i] <= 0) {
                continue;
            }
            var w1 = 2 * h[i] + h[i - 1];
            var w2 = h[i] + 2 * h[i - 1];
            m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
        }
        return m;
    }

    @Override
    public double apply(double x) {
        if (xs.length < 2) {
            return Curve.LINEAR.apply(x);
        }
        var position = Math.clamp(x, xs[0], xs[xs.length - 1]);
        var i = 0;
        while (i < xs.length - 2 && position > xs[i + 1]) {
            i++;
        }
        var h = xs[i + 1] - xs[i];
        var t = (position - xs[i]) / h;
        var t2 = t * t;
        var t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * ys[i]
                + (t3 - 2 * t2 + t) * h * slopes[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1]
                + (t3 - t2) * h * slopes[i + 1];
    }
}
