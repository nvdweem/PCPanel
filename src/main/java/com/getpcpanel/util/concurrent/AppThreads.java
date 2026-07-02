package com.getpcpanel.util.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.log4j.Log4j2;

/**
 * Central factory for the app's ad-hoc threads: every thread gets a name, an explicit daemon flag,
 * and an uncaught-exception handler that logs instead of dying silently to stderr.
 */
@Log4j2
public final class AppThreads {
    private AppThreads() {
    }

    /** Creates (but does not start) a thread with the given name and daemon flag. */
    public static Thread named(String name, boolean daemon, Runnable r) {
        var thread = new Thread(r, name);
        thread.setDaemon(daemon);
        thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread {}", t.getName(), e));
        return thread;
    }

    /** {@link ThreadFactory} variant for executors; threads are named {@code name}, {@code name-2}, ... */
    public static ThreadFactory factory(String name, boolean daemon) {
        var counter = new AtomicInteger();
        return r -> {
            var n = counter.incrementAndGet();
            return named(n == 1 ? name : name + '-' + n, daemon, r);
        };
    }
}
