package com.getpcpanel.commands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.integration.keyboard.command.CommandKeystroke;
import com.getpcpanel.integration.keyboard.command.CommandKeystroke.KeystrokeType;

/**
 * Functional tests for {@link CommandKeystroke}'s two modes and its JSON contract. The execute()
 * path is exercised by the platform keyboard tests; here we guard the data model: that profiles
 * saved before the {@code type}/{@code text} fields existed still load as plain key combinations,
 * that a TEXT keystroke round-trips, and that {@link CommandKeystroke#buildLabel()} is null-safe.
 */
@DisplayName("CommandKeystroke key vs text modes")
class CommandKeystrokeTest {
    // Command polymorphism is registered per-class (@JsonTypeName) + via the CommandModule SPI at
    // runtime, so a bare mapper must be told about the subtype it deserializes.
    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.registerSubtypes(CommandKeystroke.class);
    }

    @Test
    @DisplayName("legacy JSON without a 'type' field deserializes as a KEY keystroke")
    void legacyKeystrokeDefaultsToKey() throws Exception {
        var json = "{\"_type\":\"keyboard.keystroke\",\"keystroke\":\"ctrl+A\"}";
        var cmd = (CommandKeystroke) mapper.readValue(json, Command.class);
        assertEquals(KeystrokeType.KEY, cmd.getType());
        assertEquals("ctrl+A", cmd.getKeystroke());
        assertEquals("ctrl+A", cmd.buildLabel());
    }

    @Test
    @DisplayName("a TEXT keystroke round-trips through JSON and labels with its text")
    void textModeRoundTrips() throws Exception {
        var original = new CommandKeystroke(KeystrokeType.TEXT, null, "hello world");
        var restored = (CommandKeystroke) mapper.readValue(mapper.writeValueAsString(original), Command.class);
        assertEquals(KeystrokeType.TEXT, restored.getType());
        assertEquals("hello world", restored.getText());
        assertEquals("hello world", restored.buildLabel());
    }

    @Test
    @DisplayName("buildLabel never returns null even when the relevant field is unset")
    void buildLabelIsNullSafe() {
        assertEquals("", new CommandKeystroke(KeystrokeType.KEY, null, null).buildLabel());
        assertEquals("", new CommandKeystroke(KeystrokeType.TEXT, null, null).buildLabel());
    }
}
