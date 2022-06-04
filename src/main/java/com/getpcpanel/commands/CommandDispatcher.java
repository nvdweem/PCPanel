package com.getpcpanel.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class CommandDispatcher {
    private static final Map<String, Runnable> map = new ConcurrentHashMap<>();
    private static final HandlerThread handler;

    static {
        handler = new HandlerThread();
        handler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(handler::doStop, "CommandHandler shutdown hook"));
    }

    private CommandDispatcher() {
    }

    public static void pushVolumeChange(String serialNum, int knob, Runnable cmd) {
        map.put(String.valueOf(serialNum) + knob, cmd);
        handler.doNotify();
    }

    private static final class HandlerThread extends Thread {
        private static final Object waiter = new Object();
        private volatile boolean stopped;

        private HandlerThread() {
            super("Command Handler Thread");
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
                    cmd.run();
                    foundAnyOnPrevSweep = true;
                }
            }
        }

        private static void waitForWaiter() {
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
