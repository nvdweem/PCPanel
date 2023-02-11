package com.getpcpanel.commands;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;

public record PCPanelControlEvent(String serialNum, int knob, Command cmd, boolean initial, @Nullable Integer value, @Nullable Integer vol) {
    public Runnable buildRunnable() {
        return cmd.toRunnable(initial, serialNum, vol);
    }
}
