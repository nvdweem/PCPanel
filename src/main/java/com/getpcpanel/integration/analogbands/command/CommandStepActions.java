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
 * Turns a dial or slider into a stepper: its travel is split into {@link #stepsPerTurn} steps (up to 255, one per
 * position the hardware reports), and every step upwards runs the {@link #up} actions once, every step downwards the
 * {@link #down} actions. With a keystroke on each side this scrolls, zooms or
 * changes a brush size one notch per step.
 *
 * <p>Steps are counted from an anchor that moves a whole step at a time, so a reading that wobbles inside a step
 * never fires, and a quick turn across several steps fires once per step (up to {@link #MAX_STEPS_PER_READING}).
 * The first reading after start-up or an edit only sets the anchor. The anchor lives on this in-memory instance.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("analogbands.steps")
@CommandMeta(label = "Action per step", category = CommandCategory.system, kinds = {CommandKind.dial}, icon = "sliders")
public class CommandStepActions extends Command implements DialAction, NestedCommands {
    static final int DEFAULT_STEPS = 20;
    static final int MAX_STEPS = 255;
    static final int MAX_STEPS_PER_READING = 64;
    private static final double MAX_RAW = 255;

    /** How many steps the control's full travel is split into. */
    private final int stepsPerTurn;
    @Nullable private final Commands up;
    @Nullable private final Commands down;

    /** Position (raw 0-255) the steps are counted from; NaN until the first reading. */
    @JsonIgnore private double anchor = Double.NaN;

    @JsonCreator
    public CommandStepActions(@JsonProperty("stepsPerTurn") @Nullable Integer stepsPerTurn, @JsonProperty("up") @Nullable Commands up, @JsonProperty("down") @Nullable Commands down) {
        this.stepsPerTurn = stepsPerTurn == null || stepsPerTurn <= 0 ? DEFAULT_STEPS : Math.min(stepsPerTurn, MAX_STEPS);
        this.up = up;
        this.down = down;
    }

    @Override
    public List<Commands> nestedCommands() {
        return StreamEx.of(up, down).nonNull().toList();
    }

    @Override
    public void execute(DialActionParameters context) {
        var steps = advance(context.dial().value(), context.initial());
        var commands = steps > 0 ? up : down;
        if (steps == 0 || !Commands.hasCommands(commands)) {
            return;
        }
        // Built and run inline as button actions, so a keystroke presses once per step.
        var event = new PCPanelControlEvent(context.device(), 0, commands, false, null, PCPanelControlEvent.Source.DIAL);
        for (var i = 0; i < Math.abs(steps); i++) {
            event.buildRunnable().run();
        }
    }

    /**
     * Feeds a raw 0-255 reading and returns how many whole steps it moved past the anchor: positive upwards,
     * negative downwards, 0 when it stayed within a step (and always 0 for the initial sync). Moves the anchor by
     * the steps counted.
     */
    public synchronized int advance(int raw, boolean initial) {
        if (initial || Double.isNaN(anchor)) {
            anchor = raw;
            return 0;
        }
        var step = MAX_RAW / stepsPerTurn;
        var steps = (int) ((raw - anchor + 1e-9 * Math.signum(raw - anchor)) / step); // truncates towards zero: only whole steps count
        if (steps == 0) {
            return 0;
        }
        anchor += steps * step;
        return Math.max(-MAX_STEPS_PER_READING, Math.min(MAX_STEPS_PER_READING, steps));
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
        return stepsPerTurn + " steps";
    }
}
