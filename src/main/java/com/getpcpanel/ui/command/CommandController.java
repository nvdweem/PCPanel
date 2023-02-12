package com.getpcpanel.ui.command;

import com.getpcpanel.commands.command.Command;

import javafx.stage.Stage;

public interface CommandController<T extends Command> {
    void postInit(Stage stage, Command cmd);

    void initFromCommand(T cmd);

    Command buildCommand();
}
