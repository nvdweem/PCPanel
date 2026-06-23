package com.getpcpanel.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class CommandDispatcher {
    final Map<String, Runnable> map = new ConcurrentHashMap<>();
    final HandlerThread handler = new HandlerThread();

    @PostConstruct
    public void init() {
        handler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(handler::doStop, "CommandHandler shutdown hook"));
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

                    try {
                        cmd.run();
                    } catch (Throwable t) {
                        log.error("Error running command", t);
                    }
                    foundAnyOnPrevSweep = true;
                }
            }
        }

        private void waitForWaiter() {
            try {
                synchronized (waiter) {
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
}
