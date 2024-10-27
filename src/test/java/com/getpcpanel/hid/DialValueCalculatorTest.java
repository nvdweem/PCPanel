package com.getpcpanel.hid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.util.Util;

class DialValueCalculatorTest {

    @Test
    void calcLineair() {
        var calculator = new DialValueCalculator(new KnobSetting());
        for (var i = 0; i < 255; i++) {
            var result = Math.round(calculator.calcValue(null, i, 0, 100));
            var expected = Math.round(i / 2.55f);
            assertEquals(expected, result);
        }
    }

    @Test
    void calcUsingStartEnd() {
        var calculator = new DialValueCalculator(new KnobSetting());
        var cmd = new CommandBrightness(new DialAction.DialCommandParams(false, 25, 25));

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
}
