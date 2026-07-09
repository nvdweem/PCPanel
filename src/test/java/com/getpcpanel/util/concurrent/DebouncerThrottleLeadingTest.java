package com.getpcpanel.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of {@link Debouncer#throttleLeading}: the first call runs immediately (synchronously on
 * the caller), bursts inside the window are gated, and the most recent call is flushed once the
 * window elapses so the final value is never dropped.
 *
 * <p>Fully deterministic: the {@link Debouncer}'s scheduler/clock seam is fed a {@link VirtualScheduler}
 * whose time only moves when the test advances it, so there are no real sleeps and no timing windows.
 *
 * <p>The suite runs with the {@code per_class} test-instance lifecycle, so the fixture is rebuilt in
 * {@code @BeforeEach} rather than held in initialized fields.
 */
@DisplayName("Debouncer leading+trailing throttle")
class DebouncerThrottleLeadingTest {
    private VirtualScheduler scheduler;
    private Debouncer debouncer;
    private List<String> runs;

    @BeforeEach
    void setUp() {
        scheduler = new VirtualScheduler();
        debouncer = new Debouncer(scheduler, scheduler::now);
        runs = new ArrayList<>();
    }

    @Test
    @DisplayName("first call runs instantly; the burst's last value is flushed after the window")
    void leadingImmediateTrailingLast() {
        debouncer.throttleLeading("k", () -> runs.add("a"), 150, TimeUnit.MILLISECONDS);
        // Leading edge runs on the calling thread, so it is already recorded.
        assertEquals(List.of("a"), runs);

        // A burst inside the window: none run immediately, the last one wins the trailing flush.
        debouncer.throttleLeading("k", () -> runs.add("b"), 150, TimeUnit.MILLISECONDS);
        debouncer.throttleLeading("k", () -> runs.add("c"), 150, TimeUnit.MILLISECONDS);
        assertEquals(List.of("a"), runs, "burst must not fire immediately");

        scheduler.advanceMillis(149);
        assertEquals(List.of("a"), runs, "the trailing flush waits for the full window");
        scheduler.advanceMillis(1);
        assertEquals(List.of("a", "c"), runs, "only the leading and the final value are sent");
    }

    @Test
    @DisplayName("calls spaced beyond the window each run on their own leading edge")
    void spacedCallsEachLead() {
        debouncer.throttleLeading("k", () -> runs.add("1"), 60, TimeUnit.MILLISECONDS);
        assertEquals(List.of("1"), runs);

        scheduler.advanceMillis(120);
        debouncer.throttleLeading("k", () -> runs.add("2"), 60, TimeUnit.MILLISECONDS);
        assertEquals(List.of("1", "2"), runs);
    }

    @Test
    @DisplayName("a trailing flush restarts the window, so a call right after it is gated again")
    void trailingFlushRestartsWindow() {
        debouncer.throttleLeading("k", () -> runs.add("a"), 100, TimeUnit.MILLISECONDS);
        debouncer.throttleLeading("k", () -> runs.add("b"), 100, TimeUnit.MILLISECONDS);
        scheduler.advanceMillis(100);
        assertEquals(List.of("a", "b"), runs, "the burst's value flushes at the window edge");

        // The flush counts as a run: a call inside the next window is gated and flushed later.
        debouncer.throttleLeading("k", () -> runs.add("c"), 100, TimeUnit.MILLISECONDS);
        assertEquals(List.of("a", "b"), runs, "still inside the window started by the flush");
        scheduler.advanceMillis(100);
        assertEquals(List.of("a", "b", "c"), runs);
    }

    @Test
    @DisplayName("independent keys throttle independently")
    void keysAreIndependent() {
        debouncer.throttleLeading("k1", () -> runs.add("k1-a"), 100, TimeUnit.MILLISECONDS);
        debouncer.throttleLeading("k2", () -> runs.add("k2-a"), 100, TimeUnit.MILLISECONDS);
        assertEquals(List.of("k1-a", "k2-a"), runs, "each key gets its own leading edge");
    }

    /**
     * Single-threaded scheduler stub on a virtual clock: {@code schedule} records the task and
     * {@link #advanceMillis} moves time forward, running every task that comes due (in due order) on
     * the test thread. Only the surface the {@link Debouncer} uses is implemented.
     */
    private static final class VirtualScheduler implements ScheduledExecutorService {
        private final List<VirtualTask> tasks = new ArrayList<>();
        private long nowNanos;

        long now() {
            return nowNanos;
        }

        void advanceMillis(long millis) {
            var target = nowNanos + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                var next = tasks.stream().min(Comparator.comparingLong(t -> t.dueNanos)).orElse(null);
                if (next == null || next.dueNanos > target) {
                    break;
                }
                tasks.remove(next);
                nowNanos = next.dueNanos;
                next.task.run();
            }
            nowNanos = target;
        }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            var task = new VirtualTask(command, nowNanos + unit.toNanos(delay));
            tasks.add(task);
            return task;
        }

        @Override public void shutdown() {
        }

        @Override public List<Runnable> shutdownNow() {
            tasks.clear();
            return List.of();
        }

        @Override public boolean isShutdown() {
            return false;
        }

        @Override public boolean isTerminated() {
            return false;
        }

        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override public void execute(Runnable command) {
            command.run();
        }

        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override public java.util.concurrent.Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        private final class VirtualTask implements ScheduledFuture<Object> {
            private final Runnable task;
            private final long dueNanos;

            private VirtualTask(Runnable task, long dueNanos) {
                this.task = task;
                this.dueNanos = dueNanos;
            }

            @Override public boolean cancel(boolean mayInterruptIfRunning) {
                return tasks.remove(this);
            }

            @Override public long getDelay(TimeUnit unit) {
                return unit.convert(dueNanos - nowNanos, TimeUnit.NANOSECONDS);
            }

            @Override public int compareTo(Delayed o) {
                return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
            }

            @Override public boolean isCancelled() {
                return false;
            }

            @Override public boolean isDone() {
                return !tasks.contains(this);
            }

            @Override public Object get() {
                throw new UnsupportedOperationException();
            }

            @Override public Object get(long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
