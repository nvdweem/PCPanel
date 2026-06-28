package com.getpcpanel.analogbands.command;

import com.getpcpanel.profile.command.CommandProfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;

class CommandAnalogBandsTest {
    /** Raw 0-255 reading that lands at the given percentage of travel. */
    private static int raw(double pct) {
        return (int) Math.round(pct / 100.0 * 255.0);
    }

    private static Commands cmds(String profile) {
        return new Commands(List.of(new CommandProfile(profile)), CommandsType.allAtOnce);
    }

    /** The example layout from the feature request: 0-10 / 20-80 / 80-100, with a 10-20 gap. */
    private static CommandAnalogBands threeBands() {
        return new CommandAnalogBands(List.of(
                new AnalogBand(0, 10, "#ff0000", cmds("one")),
                new AnalogBand(20, 80, "#00ff00", cmds("two")),
                new AnalogBand(80, 100, "#0000ff", cmds("three"))));
    }

    @Test
    void bandIndexFindsContainingBandAndGaps() {
        var bands = threeBands();
        assertEquals(0, bands.bandIndexFor(raw(5)));
        assertEquals(1, bands.bandIndexFor(raw(50)));
        assertEquals(2, bands.bandIndexFor(raw(90)));
        assertEquals(-1, bands.bandIndexFor(raw(15)), "10-20 is a gap");
    }

    @Test
    void initialReadingSelectsPositionWithoutFiring() {
        var bands = threeBands();
        var t = bands.advance(raw(5), true);
        assertEquals(0, t.band());
        assertFalse(t.fire(), "syncing position on connect must not fire the action");
    }

    @Test
    void firesOnceWhenEnteringANewBand() {
        var bands = threeBands();
        bands.advance(raw(5), false); // start on position 0 (fires, but we only assert the next moves)

        var enterTwo = bands.advance(raw(50), false);
        assertEquals(1, enterTwo.band());
        assertTrue(enterTwo.fire(), "entering band 2 fires its action");

        var stayInTwo = bands.advance(raw(60), false);
        assertEquals(1, stayInTwo.band());
        assertFalse(stayInTwo.fire(), "moving within band 2 fires nothing");
    }

    @Test
    void gapActsAsDeadZoneAndKeepsTheSelectedPosition() {
        var bands = threeBands();
        bands.advance(raw(50), false); // select band 1

        var inGap = bands.advance(raw(15), false);
        assertEquals(1, inGap.band(), "a gap keeps the previously selected position");
        assertFalse(inGap.fire());

        var backToOne = bands.advance(raw(50), false);
        assertFalse(backToOne.fire(), "returning to the same position through a gap fires nothing");
    }

    @Test
    void reentryAfterMovingAwayFiresAgain() {
        var bands = threeBands();
        bands.advance(raw(50), false); // band 1
        bands.advance(raw(90), false); // band 2
        var back = bands.advance(raw(50), false);
        assertEquals(1, back.band());
        assertTrue(back.fire(), "moving back into a band re-fires it");
    }

    @Test
    void emptyBandSelectsButDoesNotFire() {
        var bands = new CommandAnalogBands(List.of(
                new AnalogBand(0, 50, "#ff0000", null),
                new AnalogBand(50, 100, "#00ff00", cmds("two"))));
        var t = bands.advance(raw(25), false);
        assertEquals(0, t.band());
        assertFalse(t.fire(), "a band with no commands selects but fires nothing");
    }

    @Test
    void currentColorFollowsSelectedPosition() {
        var bands = threeBands();
        bands.advance(raw(50), false);
        assertEquals("#00ff00", bands.getCurrentColor());
        bands.advance(raw(15), false); // gap keeps last colour
        assertEquals("#00ff00", bands.getCurrentColor());
        bands.advance(raw(90), false);
        assertEquals("#0000ff", bands.getCurrentColor());
    }
}
