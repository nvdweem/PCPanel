package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockStateTrackerTest {
    // The project runs tests per-class (see junit-platform.properties), so rebuild state every method.
    private LockStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new LockStateTracker();
    }

    /** Feeds samples and collects whatever was fired, so a test reads as a timeline. */
    private List<SystemEventType> feed(boolean... lockedSamples) {
        var fired = new ArrayList<SystemEventType>();
        for (var locked : lockedSamples) {
            var event = tracker.sample(locked);
            if (event != null) {
                fired.add(event);
            }
        }
        return fired;
    }

    @Test
    void staysQuietWhileNothingChanges() {
        assertEquals(List.of(), feed(false, false, false, false, false));
    }

    /**
     * The bug behind #145: OpenInputDesktop also returns NULL while the interactive desktop is still
     * being set up right after logon, during a desktop switch and while the UAC secure desktop is up.
     * A one- or two-poll blip must not be reported as a lock — it blacks out every panel, and if its
     * matching unlock is missed the panels stay dark until the user touches a lighting setting.
     */
    @Test
    void transientOpenInputDesktopFailureIsNotALock() {
        assertEquals(List.of(), feed(false, true, false, false));
        assertEquals(List.of(), feed(false, true, true, false, false));
    }

    @Test
    void sustainedLockIsReportedOnceConfirmed() {
        assertEquals(List.of(SystemEventType.locked), feed(false, true, true, true));
    }

    @Test
    void lockIsReportedOnlyOnceWhileItPersists() {
        assertEquals(List.of(SystemEventType.locked), feed(true, true, true, true, true, true));
    }

    @Test
    void unlockIsReportedAfterAConfirmedLock() {
        feed(true, true, true);
        assertEquals(List.of(SystemEventType.unlocked), feed(false, false, false));
    }

    /** A blip back to "locked" during an unlock must not cancel the pending unlock forever. */
    @Test
    void unlockSurvivesALaterBlip() {
        feed(true, true, true);
        assertEquals(List.of(SystemEventType.unlocked), feed(false, false, false, true, false, false, false));
    }

    /** Starting while genuinely locked still reports it — the confirmation is a delay, not a mute. */
    @Test
    void startingLockedIsStillReported() {
        assertEquals(List.of(SystemEventType.locked), feed(true, true, true));
    }

    @Test
    void sampleReturnsNullWhenNothingIsConfirmed() {
        assertNull(tracker.sample(true));
    }
}
