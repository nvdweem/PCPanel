package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.command.CommandBrightness;
import com.getpcpanel.device.command.DeviceCommandModule;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.voicemeeter.command.VoiceMeeterCommandModule;

/**
 * Validates that {@link CommandSubtypeRegistrar} turns the {@link CommandModule} contributions into
 * working Jackson polymorphic registration — i.e. a command contributed by a feature module (here a
 * core one and an integration one) deserializes by its {@code @JsonTypeName} id once the registrar has
 * customized the mapper. The {@code @All} injection that feeds {@code modules} at runtime is a standard
 * Quarkus mechanism already used elsewhere in the app, so it is exercised here by supplying the real
 * modules directly.
 */
@DisplayName("CommandSubtypeRegistrar wiring")
class CommandSubtypeRegistrarTest {
    @Test
    @DisplayName("module-contributed command types deserialize by their id after customize()")
    void registersModuleContributedSubtypes() throws Exception {
        var registrar = new CommandSubtypeRegistrar();
        registrar.modules = List.of(new DeviceCommandModule(), new VoiceMeeterCommandModule());

        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        registrar.customize(mapper);

        assertInstanceOf(CommandBrightness.class,
                mapper.readValue("{\"_type\":\"com.getpcpanel.commands.command.CommandBrightness\"}", Command.class));
        assertInstanceOf(CommandVoiceMeeterAdvanced.class,
                mapper.readValue("{\"_type\":\"com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced\"}", Command.class));
    }
}
