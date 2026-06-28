package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.integration.device.command.DeviceCommandModule;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.integration.voicemeeter.command.VoiceMeeterCommandModule;

/**
 * Validates {@link CommandSubtypeRegistrar}: it turns the {@link CommandModule} contributions into
 * working Jackson polymorphic registration, and provides backwards-compatible loading of the previous
 * {@code _type} ids. The {@code @All} injection that feeds {@code modules} at runtime is a standard
 * Quarkus mechanism used elsewhere in the app, so it is exercised here by supplying the real modules.
 */
@DisplayName("CommandSubtypeRegistrar wiring + backwards compatibility")
class CommandSubtypeRegistrarTest {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    {
        var registrar = new CommandSubtypeRegistrar();
        registrar.modules = List.of(new DeviceCommandModule(), new VoiceMeeterCommandModule());
        registrar.customize(mapper);
    }

    @Test
    @DisplayName("the current (nice) id deserializes")
    void niceIdsResolve() throws Exception {
        assertInstanceOf(CommandBrightness.class, mapper.readValue("{\"_type\":\"device.brightness\"}", Command.class));
        assertInstanceOf(CommandVoiceMeeterAdvanced.class, mapper.readValue("{\"_type\":\"voicemeeter.advanced\"}", Command.class));
    }

    @Test
    @DisplayName("a legacy FQCN id from an old save still loads, and re-saving converts it to the nice id")
    void legacyIdsLoadAndConvert() throws Exception {
        // an old profiles.json persisted the command's fully-qualified class name as _type
        var loaded = mapper.readValue("{\"_type\":\"com.getpcpanel.commands.command.CommandBrightness\"}", Command.class);
        assertInstanceOf(CommandBrightness.class, loaded);

        // re-saving writes the nice current id — a transparent one-way conversion, never the legacy id
        var json = mapper.writeValueAsString(loaded);
        assertTrue(json.contains("\"device.brightness\""), () -> "expected the nice id in: " + json);
        assertFalse(json.contains("commands.command.CommandBrightness"), () -> "must not re-write the legacy id: " + json);
    }
}
