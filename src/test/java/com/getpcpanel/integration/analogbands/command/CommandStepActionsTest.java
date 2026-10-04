package com.getpcpanel.integration.analogbands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandStepActionsTest {
    /** 10 steps per turn: 25.5 raw units each. */
    private static CommandStepActions tenSteps() {
        return new CommandStepActions(10, null, null);
    }

    @Test
    void initialReadingOnlySetsTheAnchor() {
        var sut = tenSteps();
        assertEquals(0, sut.advance(128, true));
        assertEquals(0, sut.advance(140, false), "less than a step from the anchor");
        assertEquals(1, sut.advance(160, false), "a whole step up");
    }

    @Test
    void firstReadingWithoutSyncAlsoOnlyAnchors() {
        assertEquals(0, tenSteps().advance(200, false));
    }

    @Test
    void countsEveryWholeStepAndKeepsTheRemainder() {
        var sut = tenSteps();
        sut.advance(0, true);
        assertEquals(3, sut.advance(80, false), "three whole steps of 25.5");
        assertEquals(0, sut.advance(85, false), "still inside the fourth step");
        assertEquals(1, sut.advance(103, false), "the fourth step completes");
    }

    @Test
    void downwardStepsAreNegative() {
        var sut = tenSteps();
        sut.advance(255, true);
        assertEquals(-2, sut.advance(200, false));
    }

    @Test
    void wobbleAroundAStepBoundaryDoesNotChatter() {
        var sut = tenSteps();
        sut.advance(0, true);
        assertEquals(1, sut.advance(26, false));
        assertEquals(0, sut.advance(24, false), "back below the boundary is less than a step down from the new anchor");
        assertEquals(0, sut.advance(27, false));
    }

    @Test
    void everyPositionAtTheMaximum() {
        var sut = new CommandStepActions(255, null, null);
        sut.advance(0, true);
        var total = 0;
        for (var raw = 1; raw <= 255; raw++) {
            total += sut.advance(raw, false);
        }
        assertEquals(255, total, "one step per hardware position");
    }

    @Test
    void defaultsAnInvalidStepCount() {
        assertEquals(CommandStepActions.DEFAULT_STEPS, new CommandStepActions(0, null, null).getStepsPerTurn());
        assertEquals(CommandStepActions.DEFAULT_STEPS, new CommandStepActions(null, null, null).getStepsPerTurn());
        assertEquals(255, new CommandStepActions(1000, null, null).getStepsPerTurn());
    }
}
