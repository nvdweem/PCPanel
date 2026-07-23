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
        assertEquals(List.of(SystemEventType.locked), feed(false, true, true, true, true, true, true));
    }

    @Test
    void unlockIsReportedAfterAConfirmedLock() {
        feed(false, true, true, true);
        assertEquals(List.of(SystemEventType.unlocked), feed(false, false, false));
    }

    /** A blip back to "locked" during an unlock must not cancel the pending unlock forever. */
    @Test
    void unlockSurvivesALaterBlip() {
        feed(false, true, true, true);
        assertEquals(List.of(SystemEventType.unlocked), feed(false, false, false, true, false, false, false));
    }

    /**
     * Field evidence from #145 (v2.0.85 still dark after a reboot): at boot an auto-started app can
     * see nothing but "locked" samples for far longer than the confirmation window — and on some
     * machines the {@code OpenInputDesktop} inference never succeeds at all. Before the desktop has
     * been observed unlocked once, "locked" is indistinguishable from "the inference is broken here",
     * and a false lock whose unlock never comes blacks the panels out permanently. So no lock is ever
     * reported before the first unlocked observation; a genuine locked start merely leaves the panels
     * lit until the first real unlock.
     */
    @Test
    void lockIsNeverReportedBeforeTheDesktopWasSeenUnlocked() {
        assertEquals(List.of(), feed(true, true, true, true, true, true, true, true, true, true));
        // The first unlocked observation arms the tracker; a later sustained lock is then reported.
        assertEquals(List.of(SystemEventType.locked), feed(false, true, true, true));
    }

    @Test
    void confirmedLockedReflectsTheConfirmedState() {
        feed(false);
        assertEquals(false, tracker.confirmedLocked());
        feed(true, true, true);
        assertEquals(true, tracker.confirmedLocked());
        feed(false, false, false);
        assertEquals(false, tracker.confirmedLocked());
    }

    @Test
    void sampleReturnsNullWhenNothingIsConfirmed() {
        assertNull(tracker.sample(true));
    }
}
