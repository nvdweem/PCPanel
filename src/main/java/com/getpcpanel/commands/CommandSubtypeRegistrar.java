package com.getpcpanel.commands;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.log4j.Log4j2;

/**
 * Registers every feature module's command classes as Jackson polymorphic subtypes of
 * {@link com.getpcpanel.commands.command.Command}. The subtypes are discovered entirely through the
 * {@link CommandModule} CDI SPI ({@code @All}), so no central list of command classes exists anywhere —
 * a new command/plugin only declares itself in its own package. Jackson reads each class's
 * {@code @JsonTypeName} for the stable persisted id.
 */
@Log4j2
@Singleton
public class CommandSubtypeRegistrar implements ObjectMapperCustomizer {
    @Inject
    @All
    List<CommandModule> modules;

    @Override
    public void customize(ObjectMapper mapper) {
        var registered = modules.stream()
                                 .flatMap(m -> m.commandTypes().stream())
                                 .distinct()
                                 .peek(mapper::registerSubtypes)
                                 .count();
        log.debug("Registered {} command subtypes from {} module(s)", registered, modules.size());
    }
}
