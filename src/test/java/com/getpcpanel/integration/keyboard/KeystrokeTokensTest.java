package com.getpcpanel.integration.keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.integration.keyboard.KeystrokeTokens.Modifier;

/** Functional tests for the keystroke token vocabulary shared by the three platform backends. */
@DisplayName("Keystroke token canonicalisation")
class KeystrokeTokensTest {

    @Nested
    @DisplayName("splitting a combo")
    class Splitting {
        @Test
        @DisplayName("yields one token per part, whitespace trimmed")
        void splitsOnPlus() {
            assertEquals(List.of("Ctrl", "Shift", "A"), KeystrokeTokens.split("Ctrl+Shift+A"));
            assertEquals(List.of("ctrl", "a"), KeystrokeTokens.split("ctrl + a"));
        }

        @Test
        @DisplayName("reads a whitespace-only token as the space bar")
        void blankTokenIsSpace() {
            assertEquals(List.of("Ctrl", "SPACE"), KeystrokeTokens.split("Ctrl+ "));
        }

        @Test
        @DisplayName("skips stray separators")
        void skipsEmptyTokens() {
            assertEquals(List.of("ctrl", "a"), KeystrokeTokens.split("ctrl++a"));
            assertEquals(List.of(), KeystrokeTokens.split(""));
        }
    }

    @Nested
    @DisplayName("modifiers")
    class Modifiers {
        @ParameterizedTest
        @CsvSource({
                "ctrl, CTRL", "control, CTRL", "ctl, CTRL",
                "shift, SHIFT",
                "alt, ALT", "option, ALT", "opt, ALT",
                "cmd, META", "command, META", "windows, META", "win, META", "meta, META", "super, META", "os, META",
        })
        @DisplayName("resolve from every accepted spelling")
        void spellings(String token, Modifier expected) {
            assertEquals(expected, KeystrokeTokens.modifier(token));
        }

        @ParameterizedTest
        @CsvSource({ "Ctrl, CTRL", "CTRL, CTRL", "Shift, SHIFT", "Alt, ALT", "Win, META" })
        @DisplayName("resolve regardless of case, so the recorder's labels are understood")
        void caseInsensitive(String token, Modifier expected) {
            assertEquals(expected, KeystrokeTokens.modifier(token));
        }

        @ParameterizedTest
        @ValueSource(strings = { "wibble", "", "A", "ArrowLeft" })
        @DisplayName("are absent for anything that does not name one")
        void unknownIsNull(String token) {
            assertNull(KeystrokeTokens.modifier(token));
        }
    }

    @Nested
    @DisplayName("keys")
    class Keys {
        @ParameterizedTest
        @CsvSource({
                "ArrowLeft, LEFT", "ArrowRight, RIGHT", "ArrowUp, UP", "ArrowDown, DOWN",
                "PageUp, PAGE_UP", "PageDown, PAGE_DOWN", "Backspace, BACK_SPACE",
                "Escape, ESCAPE", "Enter, ENTER", "Delete, DELETE", "Space, SPACE", "Plus, EQUALS",
        })
        @DisplayName("map the recorder's DOM names onto the AWT names the platform tables use")
        void domNames(String token, String expected) {
            assertEquals(expected, KeystrokeTokens.key(token));
        }

        @ParameterizedTest
        @CsvSource({
                "'-', MINUS", "'=', EQUALS", "'[', OPEN_BRACKET", "']', CLOSE_BRACKET",
                "';', SEMICOLON", "',', COMMA", "'.', PERIOD", "'/', SLASH", "'`', BACK_QUOTE",
        })
        @DisplayName("map punctuation characters to their key name")
        void punctuation(String token, String expected) {
            assertEquals(expected, KeystrokeTokens.key(token));
        }

        @ParameterizedTest
        @CsvSource({ "'<', COMMA", "'?', SLASH", "'_', MINUS", "'+', EQUALS", "'!', 1", "')', 0" })
        @DisplayName("map a shifted character to the key that produces it")
        void shiftedPunctuation(String token, String expected) {
            assertEquals(expected, KeystrokeTokens.key(token));
        }

        @Test
        @DisplayName("uppercase, and pass unknown names through unchanged")
        void passThrough() {
            assertEquals("A", KeystrokeTokens.key("a"));
            assertEquals("PAGE_UP", KeystrokeTokens.key("page_up"));
            assertEquals("F13", KeystrokeTokens.key("F13"));
            assertEquals("NOPE", KeystrokeTokens.key("nope"));
        }

        @Test
        @DisplayName("read a blank token as the space bar")
        void blankIsSpace() {
            assertEquals("SPACE", KeystrokeTokens.key(" "));
        }
    }

    @Nested
    @DisplayName("under a locale with its own case rules")
    class TurkishLocale {
        private final Locale original = Locale.getDefault();

        @AfterEach
        void restoreLocale() {
            Locale.setDefault(original);
        }

        @Test
        @DisplayName("resolves the same, since case folding is locale-independent")
        void turkishDottedIDoesNotBreakLookup() {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(Modifier.META, KeystrokeTokens.modifier("WINDOWS"));
            assertEquals(Modifier.SHIFT, KeystrokeTokens.modifier("SHIFT"));
            assertEquals(Modifier.CTRL, KeystrokeTokens.modifier("Ctrl"));
            assertEquals("I", KeystrokeTokens.key("i"));
        }
    }
}
