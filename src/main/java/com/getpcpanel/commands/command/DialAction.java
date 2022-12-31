package com.getpcpanel.commands.command;

public interface DialAction {
    void execute(DialActionParameters context);

    default Runnable toRunnable(DialActionParameters context) {
        return () -> execute(context);
    }

    default boolean hasOverlay() {
        return true;
    }

    record DialActionParameters(String device, boolean initial, int dial) {
    }
}
