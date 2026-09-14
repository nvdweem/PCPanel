package com.getpcpanel.commands;

import javax.annotation.Nullable;

import com.getpcpanel.commands.DialValue;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.TemplateScope;

import one.util.streamex.StreamEx;

public record PCPanelControlEvent(String serialNum, int knob, Commands cmd, boolean initial, @Nullable DialValue vol, Source source) {
    /** What produced this event. {@link CommandDispatcher} folds it into its map key so a button's
     *  press and release — which share the same {@code knob} index — never overwrite each other on a
     *  quick tap (the press would otherwise be dropped). */
    public enum Source {
        DIAL, PRESS, RELEASE
    }

    public Runnable buildRunnable() {
        Runnable run = switch (cmd.getType()) {
            case allAtOnce -> () -> StreamEx.of(cmd.getCommands()).map(c -> c.toRunnable(initial, serialNum, vol)).forEach(Runnable::run);
            case sequential -> () -> {
                var idx = incBetween(cmd.getSequenceIdx(), cmd.getCommands().size());
                cmd.setSequenceIdx(idx);
                cmd.getCommands().get(idx).toRunnable(initial, serialNum, vol).run();
            };
        };
        return () -> TemplateContext.run(templateScope(), run);
    }

    /** The control this event is for, as the scope its actions' templates render in. */
    public TemplateScope templateScope() {
        return new TemplateScope(serialNum, knob, source != Source.DIAL, cmd, vol, null, null);
    }

    private int incBetween(int value, int high) {
        return Math.max(0, Math.min(value + 1, high)) % high;
    }
}
