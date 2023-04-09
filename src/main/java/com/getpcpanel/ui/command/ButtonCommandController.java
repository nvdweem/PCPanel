package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

public abstract class ButtonCommandController<T extends Command> extends CommandController<T> {
    public abstract Command buildCommand();

    @Override
    protected Command buildLabelCommand() {
        return buildCommand();
    }
}
