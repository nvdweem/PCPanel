package com.getpcpanel.integration.display.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/** Display feature module: registers its command types via the {@link CommandModule} SPI. */
@ApplicationScoped
public class DisplayCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(CommandDisplaysOff.class);
    }
}
