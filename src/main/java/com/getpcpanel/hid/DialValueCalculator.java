package com.getpcpanel.hid;

import static com.getpcpanel.util.Util.map;

import javax.annotation.Nullable;

import com.getpcpanel.profile.KnobSetting;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
@SuppressWarnings("NumericCastThatLosesPrecision")
public class DialValueCalculator {
    public static final double EXP_CONST = 1.04723275; // This will make 0-100 map to 1-101 exponentially
    boolean logarithmic;
    int minTrim;
    int maxTrim;
    int value;

    public DialValueCalculator(@Nullable KnobSetting settings, int value) {
        if (settings != null) {
            logarithmic = settings.isLogarithmic();
            minTrim = settings.getMinTrim();
            maxTrim = settings.getMaxTrim();
        } else {
            logarithmic = false;
            minTrim = 0;
            maxTrim = 100;
        }
        this.value = value;
    }

    public int calcValue(boolean invert) {
        return (int) calcValue(invert, 0f, 100f);
    }

    public float calcValue(boolean invert, float min, float max) {
        var calc = withAppliedLog();
        var minTrimValue = map(minTrim, 0, 100, min, max);
        var maxTrimValue = map(maxTrim, 0, 100, min, max);
        var trimmed = map(calc, 0, 255, minTrimValue, maxTrimValue);
        return invert ? max - trimmed : trimmed;
    }

    private float withAppliedLog() {
        if (!logarithmic) {
            return value;
        }
        return (float) ((Math.round(Math.pow(EXP_CONST, value / 2.55d)) - 1) * 2.55d);
    }
}
