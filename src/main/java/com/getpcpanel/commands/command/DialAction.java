package com.getpcpanel.commands.command;

public interface DialAction {
    void execute(DialActionParameters context);

    default Runnable toRunnable(DialActionParameters context) {
        return () -> execute(invertIfNeeded(context));
    }

    default boolean hasOverlay() {
        return true;
    }

    boolean isInvert();

    private DialActionParameters invertIfNeeded(DialActionParameters params) {
        return isInvert() ? new DialActionParameters(params.device(), params.initial(), 100 - params.dial()) : params;
    }

    record DialActionParameters(String device, boolean initial, int dial) {
    }
}
