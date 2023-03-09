package com.getpcpanel.commands.command;

import com.getpcpanel.hid.DialValueCalculator;

public interface DialAction {
    void execute(DialActionParameters context);

    default Runnable toRunnable(DialActionParameters context) {
        return () -> execute(context);
    }

    default boolean hasOverlay() {
        return true;
    }

    boolean isInvert();

    record DialActionParameters(String device, boolean initial, DialValueCalculator dial) {
    }
}
