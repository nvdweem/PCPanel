package com.getpcpanel.device.provider.pcpanel;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

/**
 * The reports waiting to be written to one PCPanel. Messages ({@link #enqueue}) are all written, in order. Lighting
 * ({@link #replaceLighting}) is always a complete description of the LED state, so a newer one replaces one that
 * has not been written yet: a fast brightness sweep or a burst of colour changes can't build a backlog of stale
 * states that the panel then plays back late. Lighting holds a single place in the order, so the panel always
 * ends on the newest state and still gets the init message before the lighting that follows it.
 *
 * <p>A panel can drop its LEDs on its own while it keeps working; it stays dark until it is sent lighting again.
 * {@link #refreshLighting} repeats the last state taken for writing once none has gone out for an interval: every
 * lighting state is complete, so repeating it restores exactly what the panel was last told, dark frames included.
 */
final class HidOutputQueue {
    /** Queue entry standing for "the newest pending lighting", resolved when the writer reaches it. */
    private static final byte[][] LIGHTING = new byte[0][];

    private final BlockingQueue<byte[][]> queue = new LinkedBlockingQueue<>();
    private final AtomicReference<byte[][]> pendingLighting = new AtomicReference<>();
    /** Entries queued or taken but not yet {@link #written()}. Raised before an entry becomes visible to the writer. */
    private final AtomicInteger unwritten = new AtomicInteger();
    /** Nanosecond clock, as {@link System#nanoTime()}. */
    private final LongSupplier clock;
    @Nullable private volatile byte[][] lastLighting;
    private volatile long lastLightingAt;

    HidOutputQueue() {
        this(System::nanoTime);
    }

    HidOutputQueue(LongSupplier clock) {
        this.clock = clock;
    }

    void enqueue(byte[]... reports) {
        for (var report : reports) {
            unwritten.incrementAndGet();
            queue.add(new byte[][] { report });
        }
    }

    void replaceLighting(byte[]... reports) {
        // Only the transition from "nothing pending" adds a place in the queue; replacing a pending state keeps it.
        if (pendingLighting.getAndSet(reports) == null) {
            unwritten.incrementAndGet();
            queue.add(LIGHTING);
        }
    }

    /**
     * The next reports to write, or {@code null} when nothing arrived within {@code timeoutMillis}. After writing
     * non-null reports the writer calls {@link #written()}.
     */
    @Nullable
    byte[][] next(long timeoutMillis) throws InterruptedException {
        var entry = queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        //noinspection ArrayEquality
        if (entry != LIGHTING) {
            return entry;
        }
        var lighting = pendingLighting.getAndSet(null);
        if (lighting == null) {
            written(); // Unreachable while places and pending states pair up; keeps the count honest regardless.
        } else {
            lastLighting = lighting;
            lastLightingAt = clock.getAsLong();
        }
        return lighting;
    }

    /**
     * Queues the last lighting state taken for writing again when none has been taken for {@code interval}; a zero
     * interval turns this off. Never overrides lighting that is waiting to be written. True when it queued the state.
     */
    boolean refreshLighting(Duration interval) {
        var last = lastLighting;
        if (last == null || interval.isZero() || interval.isNegative() || clock.getAsLong() - lastLightingAt < interval.toNanos()) {
            return false;
        }
        // Only into an empty slot: a state requested meanwhile is newer and stays; one requested after this replaces it.
        if (!pendingLighting.compareAndSet(null, last)) {
            return false;
        }
        unwritten.incrementAndGet();
        queue.add(LIGHTING);
        return true;
    }

    void written() {
        unwritten.decrementAndGet();
    }

    /** True once everything handed to this queue has been written. */
    boolean isIdle() {
        return unwritten.get() == 0;
    }

    void clear() {
        queue.clear();
        pendingLighting.set(null);
        lastLighting = null;
        unwritten.set(0);
    }
}
