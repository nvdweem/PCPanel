package com.getpcpanel.hid;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.profile.KnobSetting;

public record DialValue(
        DialValueCalculator settings,
        int value
) {
    public DialValue(@Nullable KnobSetting settings, int value) {
        this(new DialValueCalculator(settings), value);
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public int getValue(@Nullable Command cmd) {
        return (int) getValue(cmd, 0f, 100f);
    }

    public float getValue(@Nullable Command cmd, float min, float max) {
        return settings.calcValue(cmd, value, min, max);
    }
}
