package com.getpcpanel.commands.command;

public interface DialAction {
    void execute(int value);

    default Runnable toRunnable(int dial) {
        return () -> execute(dial);
    }
}
