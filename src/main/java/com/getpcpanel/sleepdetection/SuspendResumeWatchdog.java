package com.getpcpanel.sleepdetection;

import java.util.function.Consumer;

import lombok.extern.log4j.Log4j2;

/**
 * Callback-free, cross-platform resume-from-suspend detector.
 *
 * <p>A daemon thread sleeps in short ticks and watches the wall clock. While the machine is
 * suspended the thread is frozen with it, so on resume a single {@link Thread#sleep} returns having
 * "lasted" far longer than asked — a gap well beyond {@link #SUSPEND_THRESHOLD_MS} means the host
 * slept and just woke. This needs no native callbacks (which crash the GraalVM native image), so it
 * works identically on Windows, Linux and macOS. It can only observe a resume after the fact, never
 * an impending suspend — platforms that have a real "about to sleep" signal (Linux logind) fire
 * {@link SystemEventType#goingToSuspend} themselves.
 */
@Log4j2
public final class SuspendResumeWatchdog {
    private static final long TICK_MS = 1_000L;
    /** Extra wall-clock beyond a tick that we treat as "the machine was suspended", not a GC/scheduling hiccup. */
    private static final long SUSPEND_THRESHOLD_MS = 10_000L;

    private final Consumer<SystemEventType> onEvent;
    private volatile boolean running;
    private Thread thread;

    public SuspendResumeWatchdog(Consumer<SystemEventType> onEvent) {
        this.onEvent = onEvent;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::run, "suspend-resume-watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        var last = System.currentTimeMillis();
        while (running) {
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            var now = System.currentTimeMillis();
            var gap = now - last;
            last = now;
            if (gap > TICK_MS + SUSPEND_THRESHOLD_MS) {
                log.info("Detected resume from suspend (wall-clock jumped {} ms)", gap);
                try {
                    onEvent.accept(SystemEventType.resumedFromSuspend);
                } catch (RuntimeException e) {
                    log.warn("Resume handler failed", e);
                }
            }
        }
    }
}
