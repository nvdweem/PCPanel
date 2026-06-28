package com.getpcpanel.commands.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Engine feature module: registers its own command types via the {@link com.getpcpanel.commands.CommandModule}
 * SPI. Adding/removing a command touches only this package.
 */
@ApplicationScoped
public class EngineCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(
                CommandNoOp.class);
    }
}
