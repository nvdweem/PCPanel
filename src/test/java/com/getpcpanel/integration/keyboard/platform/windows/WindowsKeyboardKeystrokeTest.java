package com.getpcpanel.integration.keyboard.platform.windows;

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
    @CsvSource({ "ctrl, 0xA2", "shift, 0xA0", "alt, 0xA4", "cmd, 0x5B", "command, 0x5B", "windows, 0x5B", "meta, 0x5B" })
    @DisplayName("modifiers map to the left-hand Win32 VK code")
    void modifiersMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.modifierVk(token));
    }

    @ParameterizedTest
    @CsvSource({ "Ctrl, 0xA2", "CTRL, 0xA2", "Shift, 0xA0", "Alt, 0xA4", "Win, 0x5B" })
    @DisplayName("the key recorder's modifier labels map to the same codes")
    void recordedModifiersMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.modifierVk(token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "wibble", "" })
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
    @CsvSource({ "ArrowLeft, 0x25", "ArrowDown, 0x28", "PageUp, 0x21", "Backspace, 0x08", "Escape, 0x1B", "Enter, 0x0D", "Space, 0x20", "',', 0xBC" })
    @DisplayName("the key recorder's key names map to the expected VK code")
    void recordedKeysMap(String token, String expectedHex) {
        assertEquals(Integer.decode(expectedHex).intValue(), WindowsKeyboard.keyVk(token));
    }

    @Test
    @DisplayName("a combo recorded in the UI resolves end to end")
    void recordedComboResolves() {
        assertEquals(0xA2, WindowsKeyboard.modifierVk("Ctrl"));
        assertEquals(0xA0, WindowsKeyboard.modifierVk("Shift"));
        assertEquals(0x25, WindowsKeyboard.keyVk("ArrowLeft"));
    }

    @ParameterizedTest
    @CsvSource({ "LEFT", "UP", "RIGHT", "DOWN", "HOME", "END", "PAGE_UP", "PAGE_DOWN", "INSERT", "DELETE" })
    @DisplayName("navigation keys are sent as extended, so a held Shift survives them")
    void navigationKeysAreExtended(String token) {
        var vk = WindowsKeyboard.keyVk(token);
        assertEquals(0x0001, WindowsKeyboard.vkFlags(vk, false) & 0x0001, token + " must set KEYEVENTF_EXTENDEDKEY");
        assertEquals(0x0003, WindowsKeyboard.vkFlags(vk, true), token + " release must keep both flags");
    }

    @Test
    @DisplayName("the Windows modifier is extended; the other modifiers and ordinary keys are not")
    void onlyTheRightKeysAreExtended() {
        assertEquals(0x0001, WindowsKeyboard.vkFlags(WindowsKeyboard.modifierVk("Win"), false));
        assertEquals(0, WindowsKeyboard.vkFlags(WindowsKeyboard.modifierVk("Shift"), false));
        assertEquals(0, WindowsKeyboard.vkFlags(WindowsKeyboard.keyVk("A"), false));
        assertEquals(0, WindowsKeyboard.vkFlags(WindowsKeyboard.keyVk("ENTER"), false));
        assertEquals(0x0002, WindowsKeyboard.vkFlags(WindowsKeyboard.keyVk("A"), true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "UNDEFINED", "NOPE", "F13" })
    @DisplayName("unrecognised keys resolve to 0 (note: F13-F24 are not yet mapped on Windows)")
    void unknownKeysAreZero(String token) {
        assertEquals(0, WindowsKeyboard.keyVk(token));
    }
}
