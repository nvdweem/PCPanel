package com.getpcpanel.voicemeeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

/**
 * Pure-logic tests for {@link ControlType}, the Voicemeeter strip/bus selector. The enum's
 * name parsing/formatting is the part of the Voicemeeter feature that has no dependency on the
 * Voicemeeter DLL, so it is unit-testable without the native library.
 */
@DisplayName("Voicemeeter ControlType parsing")
class VoicemeeterControlTypeTest {

    @ParameterizedTest
    @ValueSource(strings = { "input", "INPUT", "Input" })
    @DisplayName("'input' (any case) maps to STRIP")
    void inputIsStrip(String in) {
        assertEquals(ControlType.STRIP, ControlType.fromDn(in));
    }

    @ParameterizedTest
    @ValueSource(strings = { "output", "OUTPUT", "Output" })
    @DisplayName("'output' (any case) maps to BUS")
    void outputIsBus(String in) {
        assertEquals(ControlType.BUS, ControlType.fromDn(in));
    }

    @ParameterizedTest
    @ValueSource(strings = { "strip", "bus", "", "nonsense" })
    @DisplayName("unrecognised display names map to null")
    void unknownIsNull(String in) {
        assertNull(ControlType.fromDn(in));
    }

    @Test
    @DisplayName("getName() title-cases the enum constant")
    void getNameTitleCases() {
        assertEquals("Strip", ControlType.STRIP.getName());
        assertEquals("Bus", ControlType.BUS.getName());
    }

    @Test
    @DisplayName("toString() returns the display name used by the UI/config")
    void toStringIsDisplayName() {
        assertEquals("Input", ControlType.STRIP.toString());
        assertEquals("Output", ControlType.BUS.toString());
    }
}
