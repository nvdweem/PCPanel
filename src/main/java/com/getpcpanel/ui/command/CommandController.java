package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

public interface CommandController<T extends Command> {
    void postInit(CommandContext context);

    void initFromCommand(T cmd);
}
