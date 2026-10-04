package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PickupTest {
    private static final long LATER = Pickup.SETTLE_MS + 1;

    @Test
    void drivesTheTargetWhileNothingElseChangesIt() {
        var sut = new Pickup();
        sut.set(0.50f, 0);
        assertTrue(sut.allow(0.55f, 0.50f, LATER));
        assertTrue(sut.allow(0.60f, 0.55f, 2 * LATER));
    }

    @Test
    void waitsAfterAnOutsideChangeUntilTheControlReachesIt() {
        var sut = new Pickup();
        sut.set(0.50f, 0);
        // Someone set the app to 20% in the Windows mixer; the control is still at 52%.
        assertFalse(sut.allow(0.52f, 0.20f, LATER), "must not snap the app back up");
        assertFalse(sut.allow(0.40f, 0.20f, LATER + 10));
        assertTrue(sut.allow(0.22f, 0.20f, LATER + 20), "within the pickup window");
        assertTrue(sut.allow(0.30f, 0.22f, LATER + 30), "and drives it from then on");
    }

    @Test
    void crossingTheLevelAlsoTakesOver() {
        var sut = new Pickup();
        sut.set(0.50f, 0);
        assertFalse(sut.allow(0.55f, 0.80f, LATER), "below the new level");
        assertTrue(sut.allow(0.90f, 0.80f, LATER + 10), "jumped past it in one reading");
    }

    @Test
    void aStaleReadBackRightAfterAWriteIsNotAnOutsideChange() {
        var sut = new Pickup();
        sut.set(0.50f, 0);
        assertTrue(sut.allow(0.60f, 0.40f, 100), "the read-back still lags behind our own write");
    }

    @Test
    void unknownLevelNeverBlocks() {
        var sut = new Pickup();
        assertTrue(sut.allow(0.50f, null, 0));
    }

    @Test
    void anotherTargetWaitsAtOnce() {
        var sut = new Pickup();
        sut.set(0.60f, 0);
        assertTrue(sut.allow(0.61f, 0.60f, 10, "firefox.exe"));
        // Focus moves to Spotify, at 20%, a moment later: no settling time, the dial must not pull it up to 61%.
        assertFalse(sut.allow(0.62f, 0.20f, 20, "spotify.exe"));
        assertTrue(sut.allow(0.21f, 0.20f, 30, "spotify.exe"), "picks it up once it reaches Spotify's level");
    }

    @Test
    void anotherTargetAtTheSameLevelIsDrivenRightAway() {
        var sut = new Pickup();
        sut.set(0.60f, 0);
        assertTrue(sut.allow(0.61f, 0.60f, 10, "firefox.exe"));
        assertTrue(sut.allow(0.62f, 0.61f, 20, "spotify.exe"));
    }
}
