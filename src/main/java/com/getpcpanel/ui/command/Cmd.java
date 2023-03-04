package com.getpcpanel.ui.command;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.getpcpanel.commands.command.Command;

@Retention(RetentionPolicy.RUNTIME)
public @interface Cmd {
    String name();

    String fxml();

    Class<? extends Command>[] cmds();

    String os() default "*";

    Class<? extends CmdEnabled> enabled() default CmdEnabled.class;

    enum Type {
        button, dial
    }

    class CmdEnabled {
        public boolean isEnabled() {
            return true;
        }
    }
}
