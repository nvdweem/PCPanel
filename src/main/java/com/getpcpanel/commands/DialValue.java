package com.getpcpanel.commands;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.profile.dto.KnobSetting;

public record DialValue(
        DialValueCalculator settings,
        int value
) {
    public DialValue(@Nullable KnobSetting settings, Curve curve, int value) {
        this(new DialValueCalculator(settings, curve), value);
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public int getValue(@Nullable Command cmd) {
        return (int) getValue(cmd, 0f, 100f);
    }

    public float getValue(@Nullable Command cmd, float min, float max) {
        return settings.calcValue(cmd, value, min, max);
    }
}
