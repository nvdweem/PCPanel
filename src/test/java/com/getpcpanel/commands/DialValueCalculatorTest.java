package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.curve.AmountCurve;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.commands.curve.Curves;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.util.Util;

class DialValueCalculatorTest {

    @Test
    void calcLineair() {
        var calculator = new DialValueCalculator(new KnobSetting(), Curve.LINEAR);
        for (var i = 0; i < 255; i++) {
            var result = Math.round(calculator.calcValue(null, i, 0, 100));
            var expected = Math.round(i / 2.55f);
            assertEquals(expected, result);
        }
    }

    @Test
    void calcUsingStartEnd() {
        var calculator = new DialValueCalculator(new KnobSetting(), Curve.LINEAR);
        var cmd = new CommandBrightness(new DialCommandParams(false, 25, 25));

        for (var i = 0; i < 255; i++) {
            var result = Math.round(calculator.calcValue(cmd, i, 0, 100));
            var position = Util.map(i, 0, 255, 0, 100);

            if (position < 25) {
                assertEquals(0, result);
            } else if (position > 75) {
                assertEquals(100, result);
            } else {
                var expected = Util.map(position, 25, 75, 0, 100);
                assertTrue(result > expected - 3 && result < expected + 3);
            }
        }
    }

    @Test
    void theCurveShapesTheValueBeforeTheTrimRangeScalesIt() {
        Curve constant = x -> 0.9;
        var untrimmed = new DialValueCalculator(new KnobSetting(), constant);
        var trimmed = new DialValueCalculator(new KnobSetting().setMaxTrim(50), constant);

        assertEquals(90f, untrimmed.calcValue(null, 128, 0, 100), 0.01f);
        assertEquals(45f, trimmed.calcValue(null, 128, 0, 100), 0.01f);
    }

    @Test
    void aNamedCurveGivesTheSameValuesAsItsShape() {
        var setting = new KnobSetting().setCurve(Curves.LOGARITHMIC_ID);
        var calculator = new DialValueCalculator(setting, Curves.resolve(setting.getCurve(), null));
        var shape = new AmountCurve(AmountCurve.LOG_AMOUNT);

        for (var i = 0; i <= 255; i++) {
            assertEquals((float) (shape.apply(i / 255d) * 100), calculator.calcValue(null, i, 0, 100), 0.01f, "at " + i);
        }
    }

    /** What the input-mapping graph draws: the window holds the whole curve, the outside holds an extreme. */
    @Test
    void theStartEndWindowFitsTheWholeCurveIntoItsPortionOfTheThrow() {
        var shape = new AmountCurve(AmountCurve.LOG_AMOUNT);
        var calculator = new DialValueCalculator(new KnobSetting(), shape);
        var cmd = new CommandBrightness(new DialCommandParams(false, 25, 25));
        var windowStart = Util.map(25, 0, 100, 0, 255);
        var windowEnd = Util.map(75, 0, 100, 0, 255);

        assertEquals(0f, calculator.calcValue(cmd, 63, 0, 100), 0.01f);
        assertEquals(100f, calculator.calcValue(cmd, 192, 0, 100), 0.01f);
        for (var raw = 64; raw <= 191; raw++) {
            var travelled = Util.map(raw, windowStart, windowEnd, 0, 255) / 255d;
            assertEquals((float) (shape.apply(travelled) * 100), calculator.calcValue(cmd, raw, 0, 100), 0.01f, "at " + raw);
        }
    }

    @Test
    void anUnknownCurveIdBehavesLinearlyRatherThanBreakingInput() {
        var setting = new KnobSetting().setCurve("deleted-by-the-user");
        var calculator = new DialValueCalculator(setting, Curves.resolve(setting.getCurve(), null));

        assertEquals(50f, calculator.calcValue(null, 128, 0, 100), 0.3f);
    }
}
