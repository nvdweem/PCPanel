package com.getpcpanel.integration.dialvalue.command;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.NestedCommands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DeviceAction;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.command.LevelReadable;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.util.CdiHelper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

/**
 * Button action that runs dial actions as if their dial stood at {@link #value} percent: brightness to 0, an app's
 * volume to 30 %. With {@link #toggleBack} a press remembers the level the first readable nested action is at, and
 * the next press, finding the level still at {@link #value}, puts that level back.
 *
 * <p>A {@link DeviceAction}, so it knows the device the button is on. The nested actions run synchronously with
 * {@code initial = false}, straight through the dial engine without soft takeover, with a linear curve and no trim,
 * so only each action's own invert and move start/end shape the value, and their templates render as for a dial at
 * that value ({@code {{ percent }}}, {@code {{ raw }}}). Brightness is set as the device's button
 * brightness ({@link BrightnessService#setButtonBrightness}), because its dial action reads the dial's position
 * rather than the value it is run with.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("dial.set-value")
@CommandMeta(label = "Run dial actions at a level", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "sliders")
public class CommandSetDialValue extends Command implements DeviceAction, NestedCommands {
    /** How far (in percent) a level read back may sit from {@link #value} and still count as at the value. */
    private static final int AT_VALUE_TOLERANCE = 1;

    private final int value;
    @Nullable private final Commands commands;
    private final boolean toggleBack;

    /** The level a toggle press left behind, restored by the next press; null when there is none. */
    @JsonIgnore @Getter(AccessLevel.NONE) @Nullable private Integer restoreTo;

    @JsonCreator
    public CommandSetDialValue(@JsonProperty("value") int value, @JsonProperty("commands") @Nullable Commands commands, @JsonProperty("toggleBack") boolean toggleBack) {
        this.value = Math.clamp(value, 0, 100);
        this.commands = commands;
        this.toggleBack = toggleBack;
    }

    @Override
    public List<Commands> nestedCommands() {
        return commands == null ? List.of() : List.of(commands);
    }

    @Override
    public void execute(DeviceActionParameters context) {
        var targets = nextTargets();
        if (targets.isEmpty()) {
            return;
        }
        var percent = toggleBack ? toggledValue(targets, context.device()) : value;
        var dial = dialAt(percent);
        TemplateContext.run(TemplateContext.current().withDial(dial), () -> {
            for (var target : targets) {
                if (target instanceof CommandBrightness brightness) {
                    setBrightness(context.device(), Math.round(dial.getValue(brightness, 0f, 100f)));
                } else if (target instanceof DialAction action) {
                    action.execute(new DialActionParameters(context.device(), false, dial));
                }
            }
        });
    }

    /**
     * The percentage this press sets for a target whose level, read back as a dial percentage, is
     * {@code currentReadable} (null when it can't be read), where the dial and the level are the same thing.
     */
    int nextValue(@Nullable Integer currentReadable) {
        return nextValue(currentReadable, currentReadable != null && Math.abs(currentReadable - value) <= AT_VALUE_TOLERANCE);
    }

    /**
     * The percentage this press sets. {@code restorable} is the dial percentage that gives the target's current level
     * (null when it can't be read), {@code atValue} whether that level is the one {@link #value} gives. Without
     * {@link #toggleBack}, or without a level, always {@link #value}.
     */
    synchronized int nextValue(@Nullable Integer restorable, boolean atValue) {
        if (!toggleBack || restorable == null) {
            return value;
        }
        if (restoreTo != null && atValue) {
            var back = restoreTo;
            restoreTo = null;
            return back;
        }
        restoreTo = restorable;
        return value;
    }

    /** The percentage a toggle press sets, from the level of the first target whose level can be read. */
    private int toggledValue(List<Command> targets, String device) {
        for (var target : targets) {
            var level = target instanceof DialAction action ? readLevel(target, device) : null;
            if (level != null) {
                var action = (DialAction) target;
                // "At the value" is judged on the level, not the dial: every dial position in a move start/end dead
                // zone gives the same level, so the dial percentage read back from it need not be the value.
                var atValue = Math.abs(level - levelAt(action, value)) <= AT_VALUE_TOLERANCE;
                return nextValue(Math.round(dialPercentFor(action, level)), atValue);
            }
        }
        return nextValue(null, false);
    }

    private static DialValue dialAt(int percent) {
        return new DialValue(null, Curve.LINEAR, Math.round(percent * 255f / 100f));
    }

    /** The level (0-100) {@code action} is set to at dial percentage {@code percent}. */
    private static float levelAt(DialAction action, int percent) {
        return dialAt(percent).getValue((Command) action, 0f, 100f);
    }

    /** The commands this press runs: all of them, or the next one in turn for a sequential list. */
    private List<Command> nextTargets() {
        if (commands == null || !Commands.hasCommands(commands)) {
            return List.of();
        }
        var all = commands.getCommands();
        if (commands.getType() != CommandsType.sequential) {
            return all;
        }
        var idx = Math.max(0, commands.getSequenceIdx() + 1) % all.size();
        commands.setSequenceIdx(idx);
        return List.of(all.get(idx));
    }

    /** The target's level right now (0-100), or null when it can't be read. */
    @Nullable
    private static Float readLevel(Command target, String device) {
        if (target instanceof CommandBrightness) {
            return (float) brightnessNow(device);
        }
        if (target instanceof LevelReadable readable) {
            var level = readable.readLevel();
            return level == null ? null : level * 100f;
        }
        return null;
    }

    /**
     * The dial percentage at which {@code action} reaches {@code levelPercent}: the inverse of the mapping it is run
     * through here (its invert and move start/end, a linear curve, no trim), so a level read back restores to where it
     * was read.
     */
    static float dialPercentFor(DialAction action, float levelPercent) {
        var params = action.getDialParams() == null ? DialCommandParams.DEFAULT : action.getDialParams();
        var level = params.invert() ? 100f - levelPercent : levelPercent;
        var start = params.moveStartNonNull();
        var end = 100 - params.moveEndNonNull();
        return Math.clamp(start + level * (end - start) / 100f, 0f, 100f);
    }

    private static int brightnessNow(String device) {
        var runtime = CdiHelper.getBean(BrightnessService.class).runtimeBrightness(device);
        if (runtime.isPresent()) {
            return runtime.getAsInt();
        }
        return CdiHelper.getBean(DeviceHolder.class).getDevice(device).map(d -> d.lightingConfig().getGlobalBrightness()).orElse(100);
    }

    private static void setBrightness(String device, int percent) {
        CdiHelper.getBean(BrightnessService.class).setButtonBrightness(device, percent);
        CdiHelper.getBean(DeviceHolder.class).getDevice(device).ifPresent(d -> d.setLighting(d.lightingConfig(), false));
    }

    @Override
    public String buildLabel() {
        return value + "%";
    }
}
