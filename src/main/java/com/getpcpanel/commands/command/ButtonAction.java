package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

public interface ButtonAction {
    void execute();

    default Runnable toRunnable() {
        return this::execute;
    }

    default boolean hasOverlay() {
        return StringUtils.isNotBlank(getOverlayText());
    }

    default @Nullable String getOverlayText() {
        return null;
    }
}
