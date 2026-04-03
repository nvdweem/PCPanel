package com.getpcpanel.commands;

import javax.annotation.Nullable;

import com.getpcpanel.hid.DialValue;

import one.util.streamex.StreamEx;

public record PCPanelControlEvent(String serialNum, int knob, Commands cmd, boolean initial, @Nullable DialValue vol) {
    public Runnable buildRunnable() {
        return switch (cmd.getType()) {
            case allAtOnce -> () -> StreamEx.of(cmd.commands()).map(c -> c.toRunnable(initial, serialNum, vol)).forEach(Runnable::run);
            case sequential -> () -> {
                var idx = incBetween(cmd.getSequenceIdx(), cmd.commands().size());
                cmd.setSequenceIdx(idx);
                cmd.commands().get(idx).toRunnable(initial, serialNum, vol).run();
            };
        };
    }

    private int incBetween(int value, int high) {
        return Math.max(0, Math.min(value + 1, high)) % high;
    }
}
