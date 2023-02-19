package com.getpcpanel.ui.command;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.getpcpanel.commands.command.Command;

@Retention(RetentionPolicy.RUNTIME)
public @interface Cmd {
    String name();

    Type type();

    String fxml();

    Class<? extends Command>[] cmds();

    enum Type {
        button, dial
    }
}
