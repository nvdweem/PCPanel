package com.getpcpanel.integration.sonar.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Sonar feature module: registers its own command types via the {@link CommandModule} SPI. Adding or
 * removing a command touches only this package.
 */
@ApplicationScoped
public class SonarCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(CommandSonarVolume.class, CommandSonarMute.class);
    }
}
