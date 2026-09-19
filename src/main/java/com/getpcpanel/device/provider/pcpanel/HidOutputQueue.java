package com.getpcpanel.device.provider.pcpanel;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * The reports waiting to be written to one PCPanel. Messages ({@link #enqueue}) are all written, in order. Lighting
 * ({@link #replaceLighting}) is always a complete description of the LED state, so a newer one replaces one that
 * has not been written yet: a fast brightness sweep or a burst of colour changes can't build a backlog of stale
 * states that the panel then plays back late. Lighting holds a single place in the order, so the panel always
 * ends on the newest state and still gets the init message before the lighting that follows it.
 */
final class HidOutputQueue {
    /** Queue entry standing for "the newest pending lighting", resolved when the writer reaches it. */
    private static final byte[][] LIGHTING = new byte[0][];

    private final BlockingQueue<byte[][]> queue = new LinkedBlockingQueue<>();
    private final AtomicReference<byte[][]> pendingLighting = new AtomicReference<>();
    /** Entries queued or taken but not yet {@link #written()}. Raised before an entry becomes visible to the writer. */
    private final AtomicInteger unwritten = new AtomicInteger();

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
        }
        return lighting;
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
        unwritten.set(0);
    }
}
