package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;

public interface DialCommandController {
    Command buildCommand(DialCommandParams params);

    default Command buildLabelCommand() {
        return buildCommand(DialCommandParams.DEFAULT);
    }
}
