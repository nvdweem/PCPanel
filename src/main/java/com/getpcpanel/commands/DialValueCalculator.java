package com.getpcpanel.commands;

import static com.getpcpanel.util.Util.map;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Turns a raw analog reading into the value a command receives: the move start/end deadzones first, then
 * the control's response {@link Curve}, then the trim range, then invert.
 *
 * <p>The curve arrives already resolved rather than being looked up from the {@link KnobSetting}'s id, so
 * every path through the dial engine reads the same library the user edits.
 */
public class DialValueCalculator {
    private final Curve curve;
    private final int minTrim;
    private final int maxTrim;

    public DialValueCalculator(@Nullable KnobSetting setting, Curve curve) {
        this.curve = curve;
        if (setting == null) {
            minTrim = 0;
            maxTrim = 100;
        } else {
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

        var calc = withAppliedCurve(proceedValue);
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

    /** Curves work on 0..1, the dial engine on 0..255. */
    @SuppressWarnings("NumericCastThatLosesPrecision")
    private float withAppliedCurve(float value) {
        return (float) (curve.apply(value / 255d) * 255d);
    }

    record MoveResult(float newValue, boolean returnImmediate) {
    }
}
