package com.getpcpanel.hid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.profile.KnobSetting;

class DialValueTest {

    @CsvSource({
            // Linear
            "false, false, 255, 0, 1, 1",
            "false, false, 0, 0, 1, 0",
            "false, false, 127, 0, 1, 0.50",
            "true, false, 255, 0, 1, 0",
            "true, false, 0, 0, 1, 1",
            "true, false, 127, 0, 1, 0.50",

            // Logarithmic
            "false, true, 255, 0, 1, 1",
            "false, true, 0, 0, 1, 0",
            "false, true, 217, 0, 1, 0.50",
            "true, true, 255, 0, 1, 0",
            "true, true, 0, 0, 1, 1",
            "true, true, 217, 0, 1, 0.50",
    })
    @ParameterizedTest
    void testGetValue(boolean invert, boolean log, int value, float min, float max, float expected) {
        var dialValue = new DialValue(new KnobSetting().setLogarithmic(log), value);
        var calculated = dialValue.getValue(new CommandBrightness(new DialCommandParams(invert, null, null)), min, max);
        var rounded = Math.round(calculated * 100) / 100d; // Round to make the expected more usable

        assertEquals(expected, rounded);
    }
}
