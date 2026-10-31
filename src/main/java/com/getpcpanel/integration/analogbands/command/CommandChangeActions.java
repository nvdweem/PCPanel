package com.getpcpanel.integration.analogbands.command;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

import lombok.Getter;
import lombok.ToString;
import one.util.streamex.StreamEx;

/**
 * Runs button actions whenever a dial or slider moves: the {@link #up} actions once for a reading above the previous
 * one, the {@link #down} actions for one below it. The actions run with the control's position, so a template such
 * as <code>{{ percent }}</code> in a Run command passes it on (<code>--Set={{ percent }}</code>).
 *
 * <p>The first reading after start-up or an edit only records the position. It lives on this in-memory instance.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("analogbands.change")
@CommandMeta(label = "Action on change", category = CommandCategory.system, kinds = {CommandKind.dial}, icon = "sliders")
public class CommandChangeActions extends Command implements DialAction, NestedCommands {
    @Nullable private final Commands up;
    @Nullable private final Commands down;

    /** The previous raw 0-255 reading; -1 until the first one. */
    @JsonIgnore private int last = -1;

    @JsonCreator
    public CommandChangeActions(@JsonProperty("up") @Nullable Commands up, @JsonProperty("down") @Nullable Commands down) {
        this.up = up;
        this.down = down;
    }

    @Override
    public List<Commands> nestedCommands() {
        return StreamEx.of(up, down).nonNull().toList();
    }

    @Override
    public void execute(DialActionParameters context) {
        var direction = advance(context.dial().value(), context.initial());
        var commands = direction > 0 ? up : down;
        if (direction == 0 || !Commands.hasCommands(commands)) {
            return;
        }
        new PCPanelControlEvent(context.device(), 0, commands, false, context.dial(), PCPanelControlEvent.Source.DIAL).buildRunnable().run();
    }

    /**
     * Feeds a raw 0-255 reading and returns which way it moved from the previous one: 1 up, -1 down, 0 unchanged
     * (and always 0 for the initial sync or the first reading).
     */
    public synchronized int advance(int raw, boolean initial) {
        var previous = last;
        last = raw;
        if (initial || previous < 0) {
            return 0;
        }
        return Integer.signum(raw - previous);
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
