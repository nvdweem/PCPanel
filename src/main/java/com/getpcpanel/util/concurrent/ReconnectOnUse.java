package com.getpcpanel.util.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import lombok.extern.log4j.Log4j2;

/**
 * Starts a reconnect the moment a control that needs an integration is used while that integration is
 * unavailable, so a user waiting on it never waits out the reconnect loop's {@link ReconnectBackoff}.
 *
 * <p>Rate-limited on the leading edge: the first request runs the attempt at once, and further requests
 * are ignored while that attempt runs and for {@code cooldownMs} after it started. A dial sweep sends
 * dozens of commands a second, and all of them together start one reconnect. The attempt runs on its own
 * thread, because a request comes from the input path, which must never wait on the network.
 */
@Log4j2
public final class ReconnectOnUse {
    /** Long enough for an asynchronous connect to finish or fail before use asks for another one. */
    public static final long DEFAULT_COOLDOWN_MS = 5_000;

    private final String name;
    private final long cooldownMs;
    private final BooleanSupplier needed;
    private final Runnable attempt;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile long lastStartMs;
    private volatile boolean started;

    /**
     * @param needed  whether a reconnect would help right now: the integration is enabled and not usable.
     *                Called on every request, so it must be cheap and must not block.
     * @param attempt one reconnect attempt; typically clears the backoff and runs the regular reconnect check.
     */
    public ReconnectOnUse(String name, long cooldownMs, BooleanSupplier needed, Runnable attempt) {
        this.name = name;
        this.cooldownMs = cooldownMs;
        this.needed = needed;
        this.attempt = attempt;
    }

    public void request() {
        request(System.currentTimeMillis());
    }

    /** @return whether this request started an attempt. */
    boolean request(long nowMs) {
        if (started && nowMs - lastStartMs < cooldownMs) {
            return false;
        }
        if (!needed.getAsBoolean() || !inFlight.compareAndSet(false, true)) {
            return false;
        }
        lastStartMs = nowMs;
        started = true;
        log.debug("{} used while unavailable, reconnecting now", name);
        AppThreads.named(name + "-reconnect", true, this::runAttempt).start();
        return true;
    }

    /** Whether an attempt has started and not yet ended; it ends only after {@code attempt} returned. */
    boolean attemptRunning() {
        return inFlight.get();
    }

    private void runAttempt() {
        try {
            attempt.run();
        } finally {
            inFlight.set(false);
        }
    }
}
