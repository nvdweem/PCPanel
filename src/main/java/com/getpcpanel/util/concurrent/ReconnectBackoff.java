package com.getpcpanel.util.concurrent;

/**
 * Exponential-backoff gate for the integration reconnect loops (OBS, Wave Link, …). Each integration
 * polls on a fixed {@code @Scheduled} interval; this gate lets a tick decide whether a (re)connect is
 * actually due, so a service that is down for a long time is retried with a growing delay instead of
 * hammered on every tick.
 *
 * <p>Times are supplied by the caller ({@code System.currentTimeMillis()} in production) so the logic
 * is deterministically testable. The delay grows as {@code base, base*2, base*4, …} capped at
 * {@code max}; on a successful connection the gate resets so the next disconnect retries immediately.
 * The cap guarantees the gate always becomes ready again within {@code max} ms — a wiring bug can at
 * worst slow reconnection to once per {@code max}, never stop it.
 */
public final class ReconnectBackoff {
    private final long baseDelayMs;
    private final long maxDelayMs;
    private int failures;
    private long nextAttemptAtMs;

    public ReconnectBackoff(long baseDelayMs, long maxDelayMs) {
        if (baseDelayMs <= 0 || maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("require 0 < baseDelayMs <= maxDelayMs");
        }
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    /** @return true if a connection attempt is due at {@code nowMs}. */
    public synchronized boolean ready(long nowMs) {
        return nowMs >= nextAttemptAtMs;
    }

    /** Clears the backoff (call when connected) so the next disconnect retries without delay. */
    public synchronized void onSuccess() {
        failures = 0;
        nextAttemptAtMs = 0;
    }

    /** Schedules the next allowed attempt after an exponentially growing, capped delay. */
    public synchronized void onFailure(long nowMs) {
        var shift = Math.min(failures, 16); // 2^16 * base already exceeds any sane max; avoids overflow
        var delay = Math.min(maxDelayMs, baseDelayMs << shift);
        if (failures < Integer.MAX_VALUE) {
            failures++;
        }
        nextAttemptAtMs = nowMs + delay;
    }

    /** Consecutive failures since the last success — exposed for diagnostics/tests. */
    public synchronized int failures() {
        return failures;
    }
}
