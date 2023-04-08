package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

public interface ButtonAction {
    void execute();

    default Runnable toRunnable() {
        return this::execute;
    }

    default boolean hasOverlay() {
        return StringUtils.isNotBlank(getOverlayText());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    default @Nullable String getOverlayText() {
        return null;
    }
}
