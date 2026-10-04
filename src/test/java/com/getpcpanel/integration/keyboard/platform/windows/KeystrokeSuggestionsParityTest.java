package com.getpcpanel.integration.keyboard.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.keyboard.KeystrokeTokens;

/**
 * The keystroke field offers ready-made combos ({@code KEY_SUGGESTIONS} in the key recorder's
 * {@code key-combo.ts}). They are written on the frontend but executed here, so this test fails the build
 * when a suggestion names a modifier or key the backend does not resolve — picking it would otherwise save
 * a shortcut that silently does nothing.
 */
@DisplayName("Keystroke suggestions parity (key-combo.ts vs WindowsKeyboard)")
class KeystrokeSuggestionsParityTest {
    private static final Path KEY_COMBO_TS =
            Path.of("src", "main", "webui", "src", "app", "ui", "key-recorder", "key-combo.ts");
    private static final Pattern BLOCK =
            Pattern.compile("KEY_SUGGESTIONS\\s*:\\s*[\\w\\[\\]]+\\s*=\\s*\\[(.*?)];", Pattern.DOTALL);
    private static final Pattern VALUE = Pattern.compile("value:\\s*'([^']+)'");

    @Test
    @DisplayName("every suggestion is a combo the backend can execute")
    void everySuggestionResolves() throws IOException {
        for (var suggestion : suggestions()) {
            if (KeystrokeTokens.isLock(suggestion)) {
                continue;
            }
            var tokens = KeystrokeTokens.split(suggestion);
            for (var i = 0; i < tokens.size() - 1; i++) {
                assertNotNull(KeystrokeTokens.modifier(tokens.get(i)), "modifier '" + tokens.get(i) + "' in '" + suggestion + "'");
            }
            var key = tokens.getLast();
            if (KeystrokeTokens.wheel(key).isEmpty()) {
                assertNotEquals(0, WindowsKeyboard.keyVk(key), "key '" + key + "' in '" + suggestion + "'");
            }
        }
    }

    @Test
    @DisplayName("no two suggestions are the same combo")
    void everySuggestionIsUnique() throws IOException {
        // The list identifies an entry by its value: a second entry for the same combo shows and picks as the first.
        var values = suggestions();
        assertEquals(values.size(), new HashSet<>(values).size(), () -> "duplicate combos in " + values);
    }

    private static List<String> suggestions() throws IOException {
        assertTrue(Files.exists(KEY_COMBO_TS), () -> KEY_COMBO_TS + " not found (run from the project root)");
        var block = BLOCK.matcher(Files.readString(KEY_COMBO_TS));
        assertTrue(block.find(), () -> "KEY_SUGGESTIONS not found in " + KEY_COMBO_TS);
        List<String> values = new ArrayList<>();
        var value = VALUE.matcher(block.group(1));
        while (value.find()) {
            values.add(value.group(1));
        }
        assertTrue(values.size() > 10, () -> "parsed too few suggestions: " + values);
        return values;
    }
}
