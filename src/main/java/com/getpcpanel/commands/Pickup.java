package com.getpcpanel.commands;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Soft-takeover state of one control driving one target. After the target's level was changed somewhere else, the
 * control stops driving it until it reaches that level: within {@link #WINDOW}, or by crossing it. Without this, the
 * first nudge of the control would snap the target to wherever the control happens to be.
 *
 * <p>"Changed somewhere else" means the target is more than {@link #CHANGED} away from what this control last set,
 * judged only once the control has been still for {@link #SETTLE_MS}: right after a write, a read-back can still
 * report the level from before it. A control whose target itself changes (a focus dial when focus moves to another
 * app) meets a level it never set, so that counts at once.
 */
final class Pickup {
    static final float WINDOW = 0.03f;
    static final float CHANGED = 0.02f;
    static final long SETTLE_MS = 1_000;

    @Nullable private Float lastSet;
    private long lastSetAt;
    private boolean waiting;
    /** Which side of the target the control was on when it started waiting: -1 below, 1 above. */
    private int side;
    /** What the level was last read from, for a control whose target can change. */
    @Nullable private Object targetId;

    /** Records a level the control set without being gated (the startup sync). */
    synchronized void set(float target, long now) {
        lastSet = target;
        lastSetAt = now;
        waiting = false;
    }

    /** Whether the control may set {@code target} now, given the target currently is at {@code current}. */
    synchronized boolean allow(float target, @Nullable Float current, long now) {
        return allow(target, current, now, targetId);
    }

    /** {@link #allow(float, Float, long)} for a target identified by {@code id}; another id is another target. */
    synchronized boolean allow(float target, @Nullable Float current, long now, @Nullable Object id) {
        if (!Objects.equals(id, targetId)) {
            var before = targetId != null || lastSet != null;
            targetId = id;
            waiting = false;
            if (before && current != null && Math.abs(target - current) > WINDOW) {
                waiting = true;
                side = Float.compare(target, current);
            }
        }
        if (current == null) {
            set(target, now);
            return true;
        }
        if (!waiting && lastSet != null && now - lastSetAt >= SETTLE_MS && Math.abs(current - lastSet) > CHANGED) {
            waiting = true;
            side = Float.compare(target, current);
        }
        if (waiting) {
            var nowSide = Float.compare(target, current);
            if (Math.abs(target - current) > WINDOW && nowSide == side) {
                return false;
            }
        }
        set(target, now);
        return true;
    }
}
