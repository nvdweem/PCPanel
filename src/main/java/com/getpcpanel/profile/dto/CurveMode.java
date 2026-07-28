package com.getpcpanel.profile.dto;

/** How a {@link CurveDefinition} gets its shape. */
public enum CurveMode {
    /** A single number slides the taper between linear, logarithmic and beyond. */
    amount,
    /** Hand-placed control points, interpolated with a monotone cubic spline. */
    points
}
