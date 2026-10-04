package com.getpcpanel.device;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Filters the contact bounce of one button. The first edge passes at once and opens a window; edges inside the window
 * are absorbed, and when the window ends the settled state is emitted if it differs from what was last emitted.
 * Pure: time comes from the injected clock (milliseconds), and the caller schedules {@link #onTimer()} at
 * {@link #nextDeadline()}.
 */
public final class ButtonDebouncer {
    /** Returned by {@link #nextDeadline()} when no window is open. */
    public static final long NO_DEADLINE = -1;

    private final LongSupplier clock;
    private boolean emitted;
    private boolean settled;
    private boolean windowOpen;
    private long windowEnd;
    private int windowMs;

    public ButtonDebouncer(LongSupplier clock) {
        this.clock = clock;
    }

    /** @return edges to apply now, in order */
    public synchronized List<Boolean> onEdge(boolean pressed, int windowMs) {
        var out = new ArrayList<Boolean>(2);
        var now = clock.getAsLong();
        if (windowMs <= 0) {
            windowOpen = false;
            emit(out, pressed);
            return out;
        }
        if (windowOpen && now >= windowEnd) {
            close(out, now);
        }
        if (windowOpen) {
            settled = pressed;
            return out;
        }
        this.windowMs = windowMs;
        windowOpen = true;
        windowEnd = now + windowMs;
        settled = pressed;
        emit(out, pressed);
        return out;
    }

    /** Ends the window if its time is up; @return edges to apply now, in order */
    public synchronized List<Boolean> onTimer() {
        var out = new ArrayList<Boolean>(1);
        var now = clock.getAsLong();
        if (windowOpen && now >= windowEnd) {
            close(out, now);
        }
        return out;
    }

    /** @return the time at which {@link #onTimer()} should run, or {@link #NO_DEADLINE} */
    public synchronized long nextDeadline() {
        return windowOpen ? windowEnd : NO_DEADLINE;
    }

    /** A settled state that differs from the last one emitted goes out and opens a fresh window. */
    private void close(List<Boolean> out, long now) {
        if (settled != emitted) {
            emit(out, settled);
            windowEnd = now + windowMs;
        } else {
            windowOpen = false;
        }
    }

    private void emit(List<Boolean> out, boolean pressed) {
        emitted = pressed;
        out.add(pressed);
    }
}
