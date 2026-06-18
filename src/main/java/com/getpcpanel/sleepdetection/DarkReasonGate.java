package com.getpcpanel.sleepdetection;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tracks the independent reasons the panels should be dark and collapses them into two transitions:
 * go dark when the first reason appears, relight only when the last one clears. The reasons overlap in
 * practice — locking the workstation usually also sends the monitors to sleep — so without this a
 * "monitor on" event would relight the panels while the workstation is still locked.
 */
final class DarkReasonGate {
    enum Reason {
        suspend,
        lock,
        display
    }

    private final Set<Reason> active = EnumSet.noneOf(Reason.class);
    private final Runnable onDark;
    private final Runnable onLight;

    DarkReasonGate(Runnable onDark, Runnable onLight) {
        this.onDark = onDark;
        this.onLight = onLight;
    }

    /** Register a reason; goes dark only on the transition from "no reasons" to "some reason". */
    synchronized void add(Reason reason) {
        var wasLit = active.isEmpty();
        if (active.add(reason) && wasLit) {
            onDark.run();
        }
    }

    /** Clear a reason; relights only once the last remaining reason is gone. */
    synchronized void clear(Reason reason) {
        if (active.remove(reason) && active.isEmpty()) {
            onLight.run();
        }
    }

    /**
     * Drop every reason and relight unconditionally. Used on resume-from-suspend: the whole machine is
     * awake again, and on platforms whose callback-free detection never saw the matching suspend there
     * is no reason to clear individually.
     */
    synchronized void reset() {
        active.clear();
        onLight.run();
    }
}
