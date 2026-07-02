package com.getpcpanel.util.concurrent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Small debounce/rate-limit helper backed by a single scheduled executor.
 *
 * <p>Previously implemented with RxJava ({@code PublishSubject} + {@code debounce}/{@code throttleLatest}),
 * which pulled in the whole RxJava runtime and its computation thread pool. This plain-Java version
 * keeps the same semantics with one daemon thread and no extra dependencies.
 *
 * <p>The scheduler and the nano-time clock are injectable (package-private constructor) so tests can
 * drive time deterministically; the CDI/no-arg path uses the real single-thread scheduler and
 * {@link System#nanoTime()}.
 */
@ApplicationScoped
public class Debouncer {
    private final ScheduledExecutorService scheduler;
    private final LongSupplier nanoTime;

    public Debouncer() {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "Debouncer");
            t.setDaemon(true);
            return t;
        }), System::nanoTime);
    }

    Debouncer(ScheduledExecutorService scheduler, LongSupplier nanoTime) {
        this.scheduler = scheduler;
        this.nanoTime = nanoTime;
    }

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

    /**
     * Leading + trailing throttle: runs the first call for {@code key} immediately, then gates to at
     * most one run per {@code delay} window, and always runs the most recent call once the window
     * elapses (so the final value is never dropped). Ideal for high-frequency analog input that would
     * otherwise flood a downstream service.
     */
    public void throttleLeading(Object key, Runnable runnable, long delay, TimeUnit unit) {
        var throttle = throttles.computeIfAbsent(key, k -> new Throttle());
        var windowNanos = unit.toNanos(delay);
        Runnable leading = null;
        synchronized (throttle) {
            var now = nanoTime.getAsLong();
            if (!throttle.scheduled && (!throttle.hasRun || now - throttle.lastRunNanos >= windowNanos)) {
                throttle.hasRun = true;
                throttle.lastRunNanos = now;
                leading = runnable;                 // run outside the lock (it may do I/O)
            } else {
                throttle.latest = runnable;          // remember the newest; flush it at the window edge
                if (!throttle.scheduled) {
                    throttle.scheduled = true;
                    var remaining = Math.max(0, throttle.lastRunNanos + windowNanos - now);
                    scheduler.schedule(() -> flushLeading(throttle), remaining, TimeUnit.NANOSECONDS);
                }
            }
        }
        if (leading != null) {
            leading.run();
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

    private void flushLeading(Throttle throttle) {
        Runnable toRun;
        synchronized (throttle) {
            toRun = throttle.latest;
            throttle.latest = null;
            throttle.scheduled = false;
            if (toRun != null) {
                throttle.lastRunNanos = nanoTime.getAsLong();
            }
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
        private boolean hasRun;        // leading+trailing only: whether a leading run has happened
        private long lastRunNanos;     // leading+trailing only: timestamp of the last actual run
    }
}
