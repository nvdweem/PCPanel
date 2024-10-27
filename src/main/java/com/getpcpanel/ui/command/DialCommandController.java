package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;

public abstract class DialCommandController<T extends Command> extends CommandController<T> {
    public abstract Command buildCommand(DialCommandParams params);

    @Override
    protected Command buildLabelCommand() {
        return buildCommand(DialCommandParams.DEFAULT);
    }
}
