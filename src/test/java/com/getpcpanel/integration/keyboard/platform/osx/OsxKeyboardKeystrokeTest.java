package com.getpcpanel.integration.keyboard.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Functional tests for the macOS keystroke feature's pure token -> CGEventFlags / virtual-keycode
 * mapping (the part of {@link OsxKeyboard} that runs before the CoreGraphics CGEvent calls). The
 * mapping needs no native framework, so it is unit-testable on any OS.
 */
@DisplayName("macOS keystroke key mapping")
class OsxKeyboardKeystrokeTest {

    @ParameterizedTest
    @CsvSource({ "ctrl, 0x40000", "shift, 0x20000", "alt, 0x80000", "cmd, 0x100000", "command, 0x100000", "windows, 0x100000", "meta, 0x100000" })
    @DisplayName("modifiers map to CGEventFlags masks")
    void modifiersMap(String token, String expectedHex) {
        assertEquals(Long.decode(expectedHex).longValue(), OsxKeyboard.modifierFlag(token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "wibble", "CTRL", "" })
    @DisplayName("unknown modifiers resolve to 0")
    void unknownModifiersAreZero(String token) {
        assertEquals(0L, OsxKeyboard.modifierFlag(token));
    }

    @ParameterizedTest
    @CsvSource({ "A, 0x00", "C, 0x08", "1, 0x12", "0, 0x1D", "ENTER, 0x24", "SPACE, 0x31", "ESCAPE, 0x35", "F1, 0x7A", "F12, 0x6F", "LEFT, 0x7B" })
    @DisplayName("keys map to ANSI virtual keycodes")
    void keysMap(String token, String expectedHex) {
        assertEquals((short) Integer.decode(expectedHex).intValue(), OsxKeyboard.keyCode(token));
    }

    @Test
    @DisplayName("key lookup is case-insensitive")
    void keyLookupIsCaseInsensitive() {
        assertEquals(OsxKeyboard.keyCode("A"), OsxKeyboard.keyCode("a"));
        assertEquals(OsxKeyboard.keyCode("ENTER"), OsxKeyboard.keyCode("enter"));
    }

    @Test
    @DisplayName("unrecognised keys all return the same UNKNOWN sentinel, distinct from real keys")
    void unknownKeysShareSentinel() {
        var s1 = OsxKeyboard.keyCode("NOPE");
        var s2 = OsxKeyboard.keyCode("ALSO_NOT_A_KEY");
        assertEquals(s1, s2, "unknown keys should map to the same sentinel");
        assertNotEquals(OsxKeyboard.keyCode("A"), s1, "a real key must not collide with the sentinel");
    }
}
