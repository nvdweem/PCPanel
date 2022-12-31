package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

public interface ButtonAction {
    void execute();

    default Runnable toRunnable() {
        return this::execute;
    }

    default boolean hasOverlay() {
        return getOverlayText() != null;
    }

    default @Nullable String getOverlayText() {
        return null;
    }
}
