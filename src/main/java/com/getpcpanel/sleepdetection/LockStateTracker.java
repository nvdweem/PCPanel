package com.getpcpanel.sleepdetection;

import javax.annotation.Nullable;

/**
 * Turns a stream of raw "is the workstation locked?" samples into confirmed lock/unlock transitions.
 *
 * <p>The sampler behind it ({@code OpenInputDesktop} returning {@code NULL}) is an <em>inference</em>,
 * not an event: the call also fails while the interactive desktop is still being set up right after
 * logon, during a desktop switch, and while the UAC secure desktop is up. Reporting such a blip as a
 * lock blacks out every panel, and if the matching unlock is missed they stay dark until the user
 * touches a lighting setting — issue #145. Requiring {@link #CONFIRMATIONS} consecutive samples costs
 * a couple of seconds of detection latency (irrelevant for lighting) and filters those blips out.
 *
 * <p>This is only the fallback path: when {@code WTSRegisterSessionNotification} is available the
 * authoritative {@code WM_WTSSESSION_CHANGE} events are used instead and no sampling happens.
 *
 * <p>Not thread-safe; it is driven from a single poller thread.
 */
final class LockStateTracker {
    /** Consecutive identical samples needed before a change is believed. At 1 Hz that is ~3s. */
    static final int CONFIRMATIONS = 3;

    private boolean confirmed;
    private int streak;

    /**
     * Feeds one raw sample.
     *
     * @return the transition to report, or {@code null} when nothing changed (yet).
     */
    @Nullable
    SystemEventType sample(boolean locked) {
        if (locked == confirmed) {
            streak = 0;
            return null;
        }
        if (++streak < CONFIRMATIONS) {
            return null;
        }
        confirmed = locked;
        streak = 0;
        return locked ? SystemEventType.locked : SystemEventType.unlocked;
    }
}
