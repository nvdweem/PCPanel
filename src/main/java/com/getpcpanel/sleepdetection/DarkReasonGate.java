package com.getpcpanel.sleepdetection;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Tracks the independent reasons the panels should be dark and collapses them into two transitions:
 * go dark when the first reason appears, relight only when the last one clears. The reasons overlap in
 * practice — locking the workstation usually also sends the monitors to sleep — so without this a
 * "monitor on" event would relight the panels while the workstation is still locked.
 *
 * <p>The actions are submitted to the executor <em>in decision order, while still holding the gate's
 * lock</em>; with a single-threaded executor that means the off/relight writes reach the device queues
 * in the same order the transitions were decided. The off action used to run on a freshly spawned
 * thread while the relight ran on the caller's thread, so a quick dark→light pair (a boot-time display
 * off/on blink, a lock-inference blip) could enqueue its ALL_OFF <em>after</em> the matching relight —
 * leaving the panels dark until the user touched a lighting setting, the boot-only remainder of #145.
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
    private final Executor executor;

    DarkReasonGate(Runnable onDark, Runnable onLight, Executor executor) {
        this.onDark = onDark;
        this.onLight = onLight;
        this.executor = executor;
    }

    /** Register a reason; goes dark only on the transition from "no reasons" to "some reason". */
    synchronized void add(Reason reason) {
        var wasLit = active.isEmpty();
        if (active.add(reason) && wasLit) {
            executor.execute(onDark);
        }
    }

    /** Clear a reason; relights only once the last remaining reason is gone. */
    synchronized void clear(Reason reason) {
        if (active.remove(reason) && active.isEmpty()) {
            executor.execute(onLight);
        }
    }

    /**
     * Drop every reason and relight unconditionally. Used on resume-from-suspend: the whole machine is
     * awake again, and on platforms whose callback-free detection never saw the matching suspend there
     * is no reason to clear individually.
     */
    synchronized void reset() {
        active.clear();
        executor.execute(onLight);
    }

    /**
     * Like {@link #reset()} but only acts when something was dark. Used when the user switches sleep
     * detection off: the events that would have cleared an active reason are ignored from then on, so
     * the panels must be relit here — but a no-reason state must not trigger a gratuitous relight
     * (this runs on every settings save).
     */
    synchronized void resetIfDark() {
        if (!active.isEmpty()) {
            active.clear();
            executor.execute(onLight);
        }
    }
}
