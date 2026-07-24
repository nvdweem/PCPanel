package com.getpcpanel.integration.webui.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Web-UI feature module: registers the "open web UI" command via the {@link CommandModule} SPI.
 */
@ApplicationScoped
public class WebUiCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(CommandOpenWebUi.class);
    }
}
