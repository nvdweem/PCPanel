package com.getpcpanel.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class CommandDispatcher {
    /**
     * How long a single command may run before it is reported as stuck. Commands execute one at a time
     * on one thread, so a command that never returns takes every dial and button on every device with
     * it — and, because it is blocked rather than throwing, it does so without a word in the log. That
     * combination is indistinguishable from "the hardware died": the UI still tracks the dials and the
     * overlay still draws, since those run on the input thread. Naming the offender is what makes such
     * a freeze diagnosable at all.
     */
    static final long STUCK_COMMAND_THRESHOLD_MS = 5_000;

    final Map<String, Runnable> map = new ConcurrentHashMap<>();
    final HandlerThread handler = new HandlerThread();
    private final WatchdogThread watchdog = new WatchdogThread();
    /** The command in flight, or null when the handler is idle. Written by the handler thread only. */
    @Nullable private volatile RunningCommand running;
    /** The last command the watchdog reported, compared by identity so a repeat is reported again. */
    @Nullable private volatile RunningCommand reported;

    @PostConstruct
    public void init() {
        handler.start();
        watchdog.start();
    }

    @PreDestroy
    void stop() {
        handler.doStop();
        watchdog.interrupt();
    }

    /**
     * Reports the in-flight command when it has outlived {@link #STUCK_COMMAND_THRESHOLD_MS}, once per
     * execution. Nothing is cancelled: a command wedged in a native call cannot be interrupted safely,
     * so the only useful action is to say so.
     */
    boolean reportIfStuck(long nowMs) {
        var current = running;
        if (current == null || current == reported || nowMs - current.startedAtMs() < STUCK_COMMAND_THRESHOLD_MS) {
            return false;
        }
        reported = current;
        log.error("Command '{}' has not returned after {}ms. It is executed on the single command thread, so every dial and button on every device is blocked behind it and will do nothing until it returns.",
                current.key(), nowMs - current.startedAtMs());
        return true;
    }

    private record RunningCommand(String key, long startedAtMs) {
    }

    private CommandDispatcher() {
    }

    public void onCommand(@Observes PCPanelControlEvent event) {
        // Key by source too (not just serial+knob): a button's press and release share the same knob
        // index, so without the discriminator a quick tap could have the release overwrite the still-
        // pending press in this coalescing map and the press would be lost. The separators also remove
        // the latent serial/knob concatenation ambiguity (e.g. "AB"+1 vs "A"+"B1").
        map.put(event.serialNum() + '|' + event.source() + '|' + event.knob(), event.buildRunnable());
        handler.doNotify();
    }

    private final class HandlerThread extends Thread {
        private final Object waiter = new Object();
        private volatile boolean stopped;

        private HandlerThread() {
            super("Command Handler Thread");
            setDaemon(true);
        }

        public void doStop() {
            stopped = true;
            doNotify();
        }

        @Override
        public void run() {
            var foundAnyOnPrevSweep = false;
            while (!stopped) {
                if (!foundAnyOnPrevSweep)
                    waitForWaiter();
                foundAnyOnPrevSweep = false;
                for (var entry : map.entrySet()) {
                    var cmd = entry.getValue();
                    if (cmd == null)
                        continue;
                    map.remove(entry.getKey());

                    var current = new RunningCommand(entry.getKey(), System.currentTimeMillis());
                    running = current;
                    try {
                        cmd.run();
                    } catch (Throwable t) {
                        log.error("Error running command", t);
                    } finally {
                        running = null;
                        if (reported == current) {
                            log.warn("Command '{}' returned after {}ms; hardware control is working again.", current.key(), System.currentTimeMillis() - current.startedAtMs());
                        }
                    }
                    foundAnyOnPrevSweep = true;
                }
            }
        }

        private void waitForWaiter() {
            try {
                synchronized (waiter) {
                    // Re-check the queue under the lock before sleeping: onCommand does map.put then
                    // doNotify, so an event that arrives between our sweep and this wait would otherwise
                    // have its notify lost and sit unprocessed until the next event.
                    if (!map.isEmpty() || stopped)
                        return;
                    waiter.wait();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
            }
        }

        public void doNotify() {
            synchronized (waiter) {
                waiter.notify();
            }
        }
    }

    /** Polls the in-flight command, so a command that hangs is reported even though nothing throws. */
    private final class WatchdogThread extends Thread {
        private WatchdogThread() {
            super("Command Watchdog Thread");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    interrupt();
                    return;
                }
                reportIfStuck(System.currentTimeMillis());
            }
        }
    }
}
