package com.getpcpanel.integration.analogbands.command;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.NestedCommands;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.concurrent.Debouncer;

import lombok.Getter;
import lombok.ToString;
import one.util.streamex.StreamEx;

/**
 * Runs button actions whenever a dial or slider moves, in either direction. The actions run with the control's
 * position, so a template such as <code>{{ percent }}</code> in a Run command passes it on
 * (<code>--Set={{ percent }}</code>). A sweep is throttled to {@link #throttleMs}: the first move runs at once, then
 * at most one run per interval, and the last position always runs, so the actions end where the control stops. An
 * interval of 0 runs them on every move.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("analogbands.change")
@CommandMeta(label = "Action on change", category = CommandCategory.system, kinds = {CommandKind.dial}, icon = "sliders")
public class CommandChangeActions extends Command implements DialAction, NestedCommands {
    static final int DEFAULT_THROTTLE_MS = 150;
    static final int MAX_THROTTLE_MS = 10_000;

    @Nullable private final Commands commands;
    /** Minimum time between runs while the control moves, in milliseconds; 0 runs on every move. */
    private final int throttleMs;

    @JsonCreator
    public CommandChangeActions(@JsonProperty("commands") @Nullable Commands commands, @JsonProperty("throttleMs") @Nullable Integer throttleMs) {
        this.commands = commands;
        this.throttleMs = throttleMs == null ? DEFAULT_THROTTLE_MS : Math.clamp(throttleMs, 0, MAX_THROTTLE_MS);
    }

    @Override
    public List<Commands> nestedCommands() {
        return StreamEx.of(commands).nonNull().toList();
    }

    @Override
    public void execute(DialActionParameters context) {
        if (context.initial() || !Commands.hasCommands(commands)) {
            return;
        }
        var control = Math.max(0, TemplateContext.current().control());
        var event = new PCPanelControlEvent(context.device(), control, commands, false, context.dial(), PCPanelControlEvent.Source.DIAL);
        if (throttleMs == 0) {
            event.buildRunnable().run();
            return;
        }
        // Keyed by the control, not by `this`: a fresh instance is deserialised on every profile edit.
        CdiHelper.getBean(Debouncer.class).throttleLeading("change " + context.device() + ' ' + control, event.buildRunnable(), throttleMs, TimeUnit.MILLISECONDS);
    }

    @Override
    @Nullable
    public DialCommandParams getDialParams() {
        return null;
    }

    @Override
    public boolean hasOverlay() {
        return false;
    }

    @Override
    public String buildLabel() {
        return "On change";
    }
}
