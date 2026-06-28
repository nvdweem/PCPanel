package com.getpcpanel.obs.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Obs feature module: registers its own command types via the
 * {@link com.getpcpanel.commands.CommandModule} SPI. Adding/removing a command touches only this package.
 */
@ApplicationScoped
public class ObsCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(
                CommandObsAction.class,
                CommandObsMuteSource.class,
                CommandObsSetScene.class,
                CommandObsSetSourceVolume.class);
    }
}
