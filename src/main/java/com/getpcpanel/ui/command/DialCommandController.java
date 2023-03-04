package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

public interface DialCommandController<T extends Command> extends CommandController<T> {
    Command buildCommand(boolean invert);
}
