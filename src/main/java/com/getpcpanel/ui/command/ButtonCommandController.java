package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

public interface ButtonCommandController<T extends Command> extends CommandController<T> {
    Command buildCommand();
}
