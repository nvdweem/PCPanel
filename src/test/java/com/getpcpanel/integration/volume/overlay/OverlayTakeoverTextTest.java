package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The overlay's name while a control waits for soft takeover, with and without the "move to" text. */
class OverlayTakeoverTextTest {
    @Test
    void withTheTextItSaysWhereToMove() {
        assertEquals("Spotify · move to 40% to take over", Overlay.takeoverName("Spotify", 0.4f, true));
        assertEquals("Move to 40% to take over", Overlay.takeoverName("", 0.4f, true));
    }

    @Test
    void withoutTheTextItIsJustTheName() {
        assertEquals("Spotify", Overlay.takeoverName(" Spotify ", 0.4f, false));
        assertEquals("", Overlay.takeoverName(null, 0.4f, false));
    }
}
