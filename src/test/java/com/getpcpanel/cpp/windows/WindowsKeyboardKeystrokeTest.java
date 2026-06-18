package com.getpcpanel.cpp.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Functional tests for the Windows keystroke feature's pure token -> Win32 virtual-key mapping
 * (the part of {@link WindowsKeyboard} that runs before {@code User32.SendInput}). The mapping
 * needs no native call, so it is unit-testable on any OS.
 */
@DisplayName("Windows keystroke key mapping")
class WindowsKeyboardKeystrokeTest {

    @ParameterizedTest
    @CsvSource({ "ctrl, 0x11", "shift, 0x10", "alt, 0x12", "cmd, 0x5B", "command, 0x5B", "windows, 0x5B", "meta, 0x5B" })
    @DisplayName("modifiers map to Win32 VK codes")
    void modifiersMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.modifierVk(token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "wibble", "CTRL", "" })
    @DisplayName("unknown modifiers resolve to 0")
    void unknownModifiersAreZero(String token) {
        assertEquals(0, WindowsKeyboard.modifierVk(token));
    }

    @ParameterizedTest
    @CsvSource({ "A, 0x41", "Z, 0x5A", "a, 0x41", "0, 0x30", "9, 0x39" })
    @DisplayName("letters and digits map to their VK code (VK_A=='A', VK_0=='0')")
    void lettersAndDigitsMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.keyVk(token));
    }

    @ParameterizedTest
    @CsvSource({ "F1, 0x70", "F12, 0x7B", "ENTER, 0x0D", "TAB, 0x09", "SPACE, 0x20", "ESCAPE, 0x1B", "LEFT, 0x25", "PAGE_UP, 0x21", "COMMA, 0xBC" })
    @DisplayName("named keys map to the expected VK code")
    void namedKeysMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.keyVk(token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "UNDEFINED", "NOPE", "F13" })
    @DisplayName("unrecognised keys resolve to 0 (note: F13-F24 are not yet mapped on Windows)")
    void unknownKeysAreZero(String token) {
        assertEquals(0, WindowsKeyboard.keyVk(token));
    }
}
