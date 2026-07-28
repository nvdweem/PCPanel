package com.getpcpanel.profile.dto;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A named response curve in the user's library. {@code id} is the stable identifier a
 * {@link KnobSetting} references; {@code name} is the user-facing label.
 *
 * <p>Only the field matching {@link #mode()} shapes the curve, but the other one is kept so switching
 * between Amount and Custom in the editor and back does not discard the work.
 */
public record CurveDefinition(
        String id,
        @Nullable String name,
        @Nullable CurveMode mode,
        int amount,
        @Nullable List<CurvePoint> points
) {
    @Override
    public CurveMode mode() {
        return mode == null ? CurveMode.amount : mode;
    }

    @Override
    public List<CurvePoint> points() {
        return points == null ? List.of() : points;
    }

    @Override
    public String name() {
        return name == null ? id : name;
    }
}
