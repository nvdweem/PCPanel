package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

public abstract class DialCommandController<T extends Command> extends CommandController<T> {
    public abstract Command buildCommand(boolean invert);

    @Override
    protected Command buildLabelCommand() {
        return buildCommand(false);
    }
}
