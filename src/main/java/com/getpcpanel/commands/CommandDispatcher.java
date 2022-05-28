package com.getpcpanel.commands;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.getpcpanel.commands.command.Command;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class CommandDispatcher {
    private static final Map<String, List<? extends Command>> map = new ConcurrentHashMap<>();
    private static final HandlerThread handler;

    static {
        handler = new HandlerThread();
        handler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(handler::doStop, "CommandHandler shutdown hook"));
    }

    private CommandDispatcher() {
    }

    public static void pushVolumeChange(String serialNum, int knob, Command... cmds) {
        pushVolumeChange(serialNum, knob, Arrays.asList(cmds));
    }

    public static void pushVolumeChange(String serialNum, int knob, List<Command> cmds) {
        map.put(String.valueOf(serialNum) + knob, cmds);
        handler.doNotify();
    }

    private static void dispatchCommand(List<? extends Command> cmds) {
        for (var cmd : cmds) {
            cmd.execute();
        }
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
                    var cmds = entry.getValue();
                    if (cmds == null)
                        continue;
                    map.remove(entry.getKey());
                    dispatchCommand(cmds);
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
