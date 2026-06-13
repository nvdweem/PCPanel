package com.getpcpanel.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Small debounce/rate-limit helper backed by a single scheduled executor.
 *
 * <p>Previously implemented with RxJava ({@code PublishSubject} + {@code debounce}/{@code throttleLatest}),
 * which pulled in the whole RxJava runtime and its computation thread pool. This plain-Java version
 * keeps the same semantics with one daemon thread and no extra dependencies.
 */
@ApplicationScoped
public class Debouncer {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "Debouncer");
        t.setDaemon(true);
        return t;
    });

    private final Map<Object, ScheduledFuture<?>> debounces = new ConcurrentHashMap<>();
    private final Map<Object, Throttle> throttles = new ConcurrentHashMap<>();

    /**
     * Trailing debounce: runs {@code runnable} once, {@code delay} after the last call for {@code key}.
     * Each new call within the window resets the timer and replaces the pending runnable.
     */
    public void debounce(Object key, Runnable runnable, long delay, TimeUnit unit) {
        debounces.compute(key, (k, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(runnable, delay, unit);
        });
    }

    /**
     * Trailing rate-limit: runs at most once per {@code delay} window, always using the most recent
     * {@code runnable} supplied during that window.
     */
    public void rateLimit(Object key, Runnable runnable, long delay, TimeUnit unit) {
        var throttle = throttles.computeIfAbsent(key, k -> new Throttle());
        synchronized (throttle) {
            throttle.latest = runnable;
            if (!throttle.scheduled) {
                throttle.scheduled = true;
                scheduler.schedule(() -> flush(throttle), delay, unit);
            }
        }
    }

    private void flush(Throttle throttle) {
        Runnable toRun;
        synchronized (throttle) {
            toRun = throttle.latest;
            throttle.latest = null;
            throttle.scheduled = false;
        }
        if (toRun != null) {
            toRun.run();
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private static final class Throttle {
        private Runnable latest;
        private boolean scheduled;
    }
}
