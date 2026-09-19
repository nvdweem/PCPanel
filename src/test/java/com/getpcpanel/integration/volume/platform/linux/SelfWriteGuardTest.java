package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.linux.SndCtrlPulseAudio.SelfWriteGuard;

class SelfWriteGuardTest {
    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Test
    void recognisesAVolumeItJustWrote() {
        var guard = new SelfWriteGuard();
        guard.record(7, 42, ms(0));

        assertTrue(guard.wrote(7, 42, ms(10)));
    }

    /**
     * The case that made the dial fight force-volume: a sweep issues many writes before their pactl
     * events arrive, so the event being judged is usually not the most recent write, and may arrive
     * out of order. Every value still in the window has to count as ours.
     */
    @Test
    void recognisesAnOlderWriteFromTheSameSweep() {
        var guard = new SelfWriteGuard();
        guard.record(7, 40, ms(0));
        guard.record(7, 45, ms(10));
        guard.record(7, 50, ms(20));

        assertTrue(guard.wrote(7, 40, ms(30)), "the event for the first write arrives last");
        assertTrue(guard.wrote(7, 45, ms(30)));
        assertTrue(guard.wrote(7, 50, ms(30)));
    }

    /** A value nobody wrote is a real external change and must still be reported. */
    @Test
    void doesNotClaimAVolumeItNeverWrote() {
        var guard = new SelfWriteGuard();
        guard.record(7, 42, ms(0));

        assertFalse(guard.wrote(7, 43, ms(10)), "one step away is somebody else's change");
        assertFalse(guard.wrote(9, 42, ms(10)), "same volume, different session");
        assertFalse(new SelfWriteGuard().wrote(7, 42, ms(10)), "nothing recorded at all");
    }

    /** The guard must expire, or an app's own later change to the same value would be swallowed forever. */
    @Test
    void forgetsWritesOnceTheWindowPasses() {
        var guard = new SelfWriteGuard();
        guard.record(7, 42, ms(0));

        assertTrue(guard.wrote(7, 42, ms(700)));
        assertFalse(guard.wrote(7, 42, ms(800)));
    }

    /** Recording prunes, so a long session cannot accumulate every volume ever written. */
    @Test
    void doesNotGrowWithoutBound() {
        var guard = new SelfWriteGuard();
        for (var i = 0; i < 500; i++) {
            guard.record(7, i, ms(i * 10L));
        }

        assertFalse(guard.wrote(7, 0, ms(4990)), "the earliest writes are long expired");
        assertFalse(guard.wrote(7, 400, ms(4990)), "4s old, well outside the window");
        assertTrue(guard.wrote(7, 499, ms(4990)), "the newest write is still ours");
    }
}
