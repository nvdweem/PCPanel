package com.getpcpanel.hid;

import static com.getpcpanel.util.Util.map;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.profile.KnobSetting;

public class DialValueCalculator {
    public static final double EXP_CONST = 1.04723275; // This will make 0-100 map to 1-101 exponentially

    private final boolean logarithmic;
    private final int minTrim;
    private final int maxTrim;

    public DialValueCalculator(@Nullable KnobSetting setting) {
        if (setting == null) {
            logarithmic = false;
            minTrim = 0;
            maxTrim = 100;
        } else {
            logarithmic = setting.isLogarithmic();
            minTrim = setting.getMinTrim();
            maxTrim = setting.getMaxTrim();
        }
    }

    public float calcValue(@Nullable Command cmd, int value, float min, float max) {
        var cmdParams = (cmd instanceof DialAction da && da.getDialParams() != null) ? da.getDialParams() : DialCommandParams.DEFAULT;
        var moveResult = attemptMoveValue(cmdParams, value, min, max);
        if (moveResult.returnImmediate) {
            return moveResult.newValue;
        }
        var proceedValue = moveResult.newValue;

        var calc = withAppliedLog(proceedValue);
        var minTrimValue = map(minTrim, 0, 100, min, max);
        var maxTrimValue = map(maxTrim, 0, 100, min, max);
        var trimmed = map(calc, 0, 255, minTrimValue, maxTrimValue);
        return cmdParams.invert() ? max - trimmed : trimmed;
    }

    private MoveResult attemptMoveValue(@Nullable DialCommandParams cmd, int value, float min, float max) {
        if (cmd == null) {
            cmd = DialCommandParams.DEFAULT;
        }
        var startMoved = map(cmd.moveStartNonNull(), 0, 100, 0, 255);
        var endMoved = map(100 - cmd.moveEndNonNull(), 0, 100, 0, 255);

        if (value < startMoved) {
            return new MoveResult(cmd.invert() ? max : min, true);
        }
        if (value > endMoved) {
            return new MoveResult(cmd.invert() ? min : max, true);
        }
        return new MoveResult(map(value, startMoved, endMoved, 0, 255), false);
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private float withAppliedLog(float value) {
        if (!logarithmic) {
            return value;
        }
        return (float) ((Math.round(Math.pow(EXP_CONST, value / 2.55d)) - 1) * 2.55d);
    }

    record MoveResult(float newValue, boolean returnImmediate) {
    }
}
