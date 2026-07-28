package com.getpcpanel.profile.dto;

/**
 * One control point of a hand-drawn curve, both axes normalised to 0..1: {@code x} is the dial position,
 * {@code y} the resulting output.
 */
public record CurvePoint(double x, double y) {
}
