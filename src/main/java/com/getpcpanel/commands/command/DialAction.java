package com.getpcpanel.commands.command;

public interface DialAction {
    void execute(boolean initial, int value);

    default Runnable toRunnable(boolean initial, int dial) {
        return () -> execute(initial, dial);
    }
}
