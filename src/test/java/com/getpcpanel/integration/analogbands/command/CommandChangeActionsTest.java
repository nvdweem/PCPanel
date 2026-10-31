package com.getpcpanel.integration.analogbands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandChangeActionsTest {
    @Test
    void initialReadingOnlyRecordsThePosition() {
        var sut = new CommandChangeActions(null, null);
        assertEquals(0, sut.advance(128, true));
        assertEquals(1, sut.advance(129, false));
    }

    @Test
    void firstReadingWithoutSyncAlsoOnlyRecords() {
        assertEquals(0, new CommandChangeActions(null, null).advance(200, false));
    }

    @Test
    void reportsTheDirectionOfEveryChange() {
        var sut = new CommandChangeActions(null, null);
        sut.advance(100, true);
        assertEquals(1, sut.advance(140, false));
        assertEquals(-1, sut.advance(139, false));
        assertEquals(0, sut.advance(139, false), "an unchanged reading");
        assertEquals(-1, sut.advance(0, false));
    }
}
