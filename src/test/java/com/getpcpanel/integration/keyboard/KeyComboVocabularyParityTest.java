package com.getpcpanel.integration.keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.keyboard.KeystrokeTokens.Modifier;

/**
 * The modifier vocabulary lives in TWO places that must stay identical: {@link KeystrokeTokens} on the
 * backend, which decides what a saved combo executes as, and {@code MOD_ALIASES} in the key recorder's
 * {@code key-combo.ts}, which decides which toggle lights up when one is opened for editing. They are
 * hand-maintained on opposite sides of the REST boundary, so this test fails the build the moment they
 * drift.
 *
 * <p>Drift is silent and destructive rather than merely cosmetic: a spelling the backend honours but the
 * recorder does not is read as part of the key rather than as a modifier, so opening that combo shows the
 * modifier switched off and saving it drops the modifier from a shortcut that used to work.
 */
@DisplayName("Modifier vocabulary parity (KeystrokeTokens vs key-combo.ts)")
class KeyComboVocabularyParityTest {
    private static final Path KEY_COMBO_TS =
            Path.of("src", "main", "webui", "src", "app", "ui", "key-recorder", "key-combo.ts");
    private static final Pattern ALIASES_BLOCK =
            Pattern.compile("MOD_ALIASES\\s*:\\s*Record<string,\\s*string>\\s*=\\s*\\{(.*?)}", Pattern.DOTALL);
    private static final Pattern ENTRY = Pattern.compile("(\\w+)\\s*:\\s*'([^']+)'");

    /** The recorder's label for each backend modifier. */
    private static final Map<Modifier, String> LABELS = Map.of(
            Modifier.CTRL, "Ctrl", Modifier.SHIFT, "Shift", Modifier.ALT, "Alt", Modifier.META, "Win");

    @Test
    @DisplayName("both sides accept exactly the same modifier spellings")
    void aliasSetsMatch() {
        var frontend = frontendAliases();
        assertEquals(new TreeSet<>(KeystrokeTokens.modifierAliases()), new TreeSet<>(frontend.keySet()),
                "key-combo.ts MOD_ALIASES and KeystrokeTokens must accept the same spellings");
    }

    @Test
    @DisplayName("each spelling resolves to the same modifier on both sides")
    void aliasesAgreeOnTheModifier() {
        frontendAliases().forEach((alias, label) -> {
            var backend = KeystrokeTokens.modifier(alias);
            assertNotNull(backend, () -> "backend does not accept '" + alias + "'");
            assertEquals(LABELS.get(backend), label,
                    () -> "'" + alias + "' is " + backend + " on the backend but labelled '" + label + "' in the recorder");
        });
    }

    private static Map<String, String> frontendAliases() {
        var source = read(KEY_COMBO_TS);
        var block = ALIASES_BLOCK.matcher(source);
        assertTrue(block.find(), () -> "MOD_ALIASES not found in " + KEY_COMBO_TS);
        Map<String, String> aliases = new LinkedHashMap<>();
        var entry = ENTRY.matcher(block.group(1));
        while (entry.find()) {
            aliases.put(entry.group(1), entry.group(2));
        }
        assertTrue(aliases.size() > 4, () -> "parsed too few aliases from " + KEY_COMBO_TS + ": " + aliases);
        return aliases;
    }

    private static String read(Path path) {
        assertTrue(Files.exists(path), () -> path + " not found (run from the project root)");
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
