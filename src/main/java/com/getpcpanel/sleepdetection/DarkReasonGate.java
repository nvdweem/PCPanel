package com.getpcpanel.sleepdetection;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tracks the independent reasons the panels should be dark and collapses them into transitions: go dark
 * when the first reason appears, relight only when the last one clears. While dark, the PC is either
 * awake (only a lock and/or screens off) or asleep (suspended); a change between the two is announced
 * as going dark again, with the new state. The reasons overlap in
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
    /** Takes whether the PC is awake (no suspend among the reasons). */
    private final Consumer<Boolean> onDark;
    private final Runnable onLight;
    private final Executor executor;

    DarkReasonGate(Consumer<Boolean> onDark, Runnable onLight, Executor executor) {
        this.onDark = onDark;
        this.onLight = onLight;
        this.executor = executor;
    }

    /** Whether any reason keeps the panels dark right now. */
    synchronized boolean isDark() {
        return !active.isEmpty();
    }

    /** Whether the panels are dark for a lock and/or screens off only, with the PC awake. */
    synchronized boolean isDarkButAwake() {
        return !active.isEmpty() && !active.contains(Reason.suspend);
    }

    /**
     * Register a reason; goes dark on the transition from "no reasons" to "some reason", and again when the PC goes
     * from awake to asleep while dark.
     */
    synchronized void add(Reason reason) {
        var wasLit = active.isEmpty();
        var wasAwake = isDarkButAwake();
        if (active.add(reason) && (wasLit || wasAwake != isDarkButAwake())) {
            announceDark();
        }
    }

    /** Clear a reason; relights only once the last remaining reason is gone, and goes dark again from asleep to awake. */
    synchronized void clear(Reason reason) {
        var wasAwake = isDarkButAwake();
        if (!active.remove(reason)) {
            return;
        }
        if (active.isEmpty()) {
            executor.execute(onLight);
        } else if (wasAwake != isDarkButAwake()) {
            announceDark();
        }
    }

    private void announceDark() {
        var awake = isDarkButAwake();
        executor.execute(() -> onDark.accept(awake));
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
