package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of {@link Debouncer#throttleLeading}: the first call runs immediately (synchronously on
 * the caller), bursts inside the window are gated, and the most recent call is flushed once the
 * window elapses so the final value is never dropped.
 *
 * <p>The {@link Debouncer} is created per test method (and shut down in a finally) because the suite
 * runs with the {@code per_class} test-instance lifecycle, so a shared field would leak its scheduler
 * across methods.
 */
@DisplayName("Debouncer leading+trailing throttle")
class DebouncerThrottleLeadingTest {
    @Test
    @DisplayName("first call runs instantly; the burst's last value is flushed after the window")
    void leadingImmediateTrailingLast() throws Exception {
        var debouncer = new Debouncer();
        var runs = new CopyOnWriteArrayList<String>();
        try {
            debouncer.throttleLeading("k", () -> runs.add("a"), 150, TimeUnit.MILLISECONDS);
            // Leading edge runs on the calling thread, so it is already recorded.
            assertEquals(List.of("a"), runs);

            // A burst inside the window: none run immediately, the last one wins the trailing flush.
            debouncer.throttleLeading("k", () -> runs.add("b"), 150, TimeUnit.MILLISECONDS);
            debouncer.throttleLeading("k", () -> runs.add("c"), 150, TimeUnit.MILLISECONDS);
            assertEquals(List.of("a"), runs, "burst must not fire immediately");

            awaitSize(runs, 2, 2000);
            assertEquals(List.of("a", "c"), runs, "only the leading and the final value are sent");
        } finally {
            debouncer.shutdown();
        }
    }

    @Test
    @DisplayName("calls spaced beyond the window each run on their own leading edge")
    void spacedCallsEachLead() throws Exception {
        var debouncer = new Debouncer();
        var runs = new CopyOnWriteArrayList<String>();
        try {
            debouncer.throttleLeading("k", () -> runs.add("1"), 60, TimeUnit.MILLISECONDS);
            assertEquals(List.of("1"), runs);
            TimeUnit.MILLISECONDS.sleep(120);
            debouncer.throttleLeading("k", () -> runs.add("2"), 60, TimeUnit.MILLISECONDS);
            assertEquals(List.of("1", "2"), runs);
        } finally {
            debouncer.shutdown();
        }
    }

    private static void awaitSize(List<?> list, int size, long timeoutMs) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (list.size() < size && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(list.size() >= size, "expected at least " + size + " runs, got " + list);
    }
}
