package com.getpcpanel.commands;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds an {@link ObjectMapper} wired for the command polymorphism exactly as the app does, for
 * command-family tests that live outside the {@code com.getpcpanel.commands} package. It supplies
 * the given {@link CommandModule}s to a {@link CommandSubtypeRegistrar} and applies its
 * customization — the same path the Quarkus {@code @All} injection drives at runtime — so those
 * tests do not need access to the registrar's package-private module list.
 */
public final class CommandMapperTestFactory {
    private CommandMapperTestFactory() {
    }

    public static ObjectMapper mapperFor(CommandModule... modules) {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var registrar = new CommandSubtypeRegistrar();
        registrar.modules = List.of(modules);
        registrar.customize(mapper);
        return mapper;
    }
}
