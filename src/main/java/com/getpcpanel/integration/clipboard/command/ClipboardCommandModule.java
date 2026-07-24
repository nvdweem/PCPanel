package com.getpcpanel.integration.clipboard.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Clipboard feature module: registers its command types via the {@link CommandModule} SPI.
 */
@ApplicationScoped
public class ClipboardCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(CommandSetClipboard.class);
    }
}
