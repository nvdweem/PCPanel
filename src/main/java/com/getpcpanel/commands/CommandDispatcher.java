package com.getpcpanel.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public final class CommandDispatcher {
    private final Map<String, Runnable> map = new ConcurrentHashMap<>();
    private final HandlerThread handler = new HandlerThread();

    @PostConstruct
    public void init() {
        handler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(handler::doStop, "CommandHandler shutdown hook"));
    }

    private CommandDispatcher() {
    }

    @EventListener
    public void onCommand(PCPanelControlEvent event) {
        map.put(event.serialNum() + event.knob(), event.cmd());
        handler.doNotify();
    }

    private final class HandlerThread extends Thread {
        private final Object waiter = new Object();
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
