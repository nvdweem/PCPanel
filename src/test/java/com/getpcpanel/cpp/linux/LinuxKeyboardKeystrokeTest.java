package com.getpcpanel.cpp.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Functional tests for the Linux keystroke feature's pure string -> X11 keysym mapping (the part of
 * {@link LinuxKeyboard} that runs before any native call). This is the logic that decides whether a
 * configured keystroke can actually be synthesised; a gap here is what made F13-F24 silently
 * unsupported. Resolving the keysym needs no X display, so it is fully unit-testable.
 */
@DisplayName("Linux keystroke key mapping")
class LinuxKeyboardKeystrokeTest {

    @ParameterizedTest
    @CsvSource({
            "ctrl, 0xffe3", "shift, 0xffe1", "alt, 0xffe9",
            "cmd, 0xffeb", "command, 0xffeb", "windows, 0xffeb", "meta, 0xffeb",
    })
    @DisplayName("modifiers map to the expected X11 keysyms")
    void modifiersMap(String token, String expectedHex) {
        assertEquals(Long.decode(expectedHex).longValue(), LinuxKeyboard.modifierKeysym(token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "wibble", "", "CTRL", "control" })
    @DisplayName("unknown modifier tokens resolve to 0 (the 'bad modifier' sentinel)")
    void unknownModifiersAreZero(String token) {
        assertEquals(0L, LinuxKeyboard.modifierKeysym(token));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 12, 13, 24 })
    @DisplayName("function keys F1-F24 all resolve (regression guard for the F13-F24 gap)")
    void functionKeysResolve(int n) {
        long sym = LinuxKeyboard.keysym("F" + n);
        assertNotEquals(0L, sym, "F" + n + " should map to a keysym");
        // XK_F1..XK_F24 are contiguous from 0xffbe.
        assertEquals(0xffbeL + (n - 1), sym);
    }

    @Test
    @DisplayName("F24 maps to XK_F24 (0xffd5) — the key the native-config generator presses")
    void f24IsTheGeneratorKey() {
        assertEquals(0xffd5L, LinuxKeyboard.keysym("F24"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "A", "a", "Z", "m" })
    @DisplayName("letters map to their lowercase Latin-1 codepoint and are case-insensitive")
    void lettersMap(String token) {
        long sym = LinuxKeyboard.keysym(token);
        assertEquals(Character.toLowerCase(token.charAt(0)), sym);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "5", "9" })
    @DisplayName("digits map to their ASCII codepoint")
    void digitsMap(String token) {
        assertEquals(token.charAt(0), LinuxKeyboard.keysym(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ENTER", "TAB", "SPACE", "BACK_SPACE", "ESCAPE", "ESC", "DELETE", "INSERT",
            "HOME", "END", "PAGE_UP", "PAGE_DOWN", "LEFT", "UP", "RIGHT", "DOWN",
            "MINUS", "EQUALS", "OPEN_BRACKET", "CLOSE_BRACKET", "BACK_SLASH", "SEMICOLON",
            "QUOTE", "COMMA", "PERIOD", "SLASH", "BACK_QUOTE",
    })
    @DisplayName("every documented navigation/punctuation token resolves to a non-zero keysym")
    void namedKeysResolve(String token) {
        assertTrue(LinuxKeyboard.keysym(token) != 0L, token + " should be a supported key");
    }

    @Test
    @DisplayName("named-key lookup is case-insensitive (UI may send mixed case)")
    void namedKeysAreCaseInsensitive() {
        assertEquals(LinuxKeyboard.keysym("ENTER"), LinuxKeyboard.keysym("enter"));
        assertEquals(LinuxKeyboard.keysym("PAGE_UP"), LinuxKeyboard.keysym("page_up"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "UNDEFINED", "NOPE", "F25", "F0", "??" })
    @DisplayName("unrecognised keys resolve to 0 so executeKeyStroke skips them instead of injecting garbage")
    void unknownKeysAreZero(String token) {
        assertEquals(0L, LinuxKeyboard.keysym(token));
    }
}
