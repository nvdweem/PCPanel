package com.getpcpanel.commands;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.LevelReadable;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * "No volume jumps": a knob or slider whose target was changed somewhere else (the Windows mixer, the app itself)
 * leaves it alone until the control reaches that level, instead of snapping it to the control's position. Applies to
 * actions that can read their target's level back ({@link LevelReadable}); sliders and knobs are switched separately.
 * See {@link Pickup} for the rule.
 */
@Log4j2
@ApplicationScoped
public class SoftTakeover {
    @Inject SaveService save;
    @Inject DeviceHolder devices;

    private final Map<Key, Slot> pickups = new ConcurrentHashMap<>();

    /** What a control may run now, and the level it has to reach first when an action is held back. */
    public record Result(Commands allowed, @Nullable Waiting waiting) {
    }

    /** A held-back action's target level and where the control sits now, both 0..1. */
    public record Waiting(float current, float control) {
    }

    /**
     * Splits a control's actions into those that may run for this reading and those still waiting for the control
     * to reach their target. The startup sync ({@code initial}) is never held back; it only records what it sets.
     */
    public Result filter(String serial, int knob, Commands commands, DialValue dial, boolean initial) {
        if (!enabledFor(serial, knob)) {
            return new Result(commands, null);
        }
        var now = System.currentTimeMillis();
        var allowed = new ArrayList<Command>();
        Waiting waiting = null;
        var list = commands.getCommands();
        for (var i = 0; i < list.size(); i++) {
            var cmd = list.get(i);
            if (!(cmd instanceof LevelReadable readable) || !(cmd instanceof DialAction)) {
                allowed.add(cmd);
                continue;
            }
            var target = dial.getValue(cmd, 0, 1);
            var pickup = pickups.compute(new Key(serial, knob, i), (k, slot) -> slot != null && slot.owner() == cmd ? slot : new Slot(cmd, new Pickup())).pickup();
            if (initial) {
                pickup.set(target, now);
                allowed.add(cmd);
                continue;
            }
            var current = readable.readLevel();
            if (pickup.allow(target, current, now, readable.levelTarget())) {
                allowed.add(cmd);
            } else if (waiting == null && current != null) {
                waiting = new Waiting(current, target);
            }
        }
        if (allowed.size() == list.size()) {
            return new Result(commands, waiting);
        }
        return new Result(new Commands(allowed, commands.getType()), waiting);
    }

    private boolean enabledFor(String serial, int knob) {
        var s = save.get();
        if (!s.isSoftTakeoverKnobs() && !s.isSoftTakeoverSliders()) {
            return false;
        }
        var kind = devices.getDevice(serial)
                          .flatMap(d -> d.descriptor().analogInputs().stream().filter(a -> a.index() == knob).findFirst())
                          .map(a -> a.kind())
                          .orElse(AnalogKind.KNOB);
        return kind == AnalogKind.SLIDER ? s.isSoftTakeoverSliders() : s.isSoftTakeoverKnobs();
    }

    private record Key(String serial, int knob, int index) {
    }

    /** The action instance a pickup belongs to: an edited control gets new instances and so starts fresh. */
    private record Slot(Command owner, Pickup pickup) {
    }
}
