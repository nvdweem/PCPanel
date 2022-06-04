package com.getpcpanel.commands.command;

public interface ButtonAction {
    void execute();

    default Runnable toRunnable() {
        return this::execute;
    }
}
