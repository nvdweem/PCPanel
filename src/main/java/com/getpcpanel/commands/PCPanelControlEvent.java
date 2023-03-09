package com.getpcpanel.commands;

import javax.annotation.Nullable;

import com.getpcpanel.hid.DialValueCalculator;

import one.util.streamex.StreamEx;

public record PCPanelControlEvent(String serialNum, int knob, Commands cmd, boolean initial, @Nullable DialValueCalculator vol) {
    public Runnable buildRunnable() {
        return () -> StreamEx.of(cmd.commands()).map(c -> c.toRunnable(initial, serialNum, vol)).forEach(Runnable::run);
    }
}
