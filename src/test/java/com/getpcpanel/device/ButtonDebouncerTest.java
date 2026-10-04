package com.getpcpanel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ButtonDebouncerTest {
    private AtomicLong now;
    private ButtonDebouncer d;

    @BeforeEach void setUp() { now = new AtomicLong(); d = new ButtonDebouncer(now::get); }

    @Test void firstEdgePassesAtOnce() { assertEquals(List.of(true), d.onEdge(true, 50)); }

    @Test void chatterInsideTheWindowIsIgnored() {
        d.onEdge(true, 50);
        now.set(10); assertTrue(d.onEdge(false, 50).isEmpty());
        now.set(20); assertTrue(d.onEdge(true, 50).isEmpty());
        now.set(60); assertTrue(d.onTimer().isEmpty()); // settled pressed, already emitted
    }

    @Test void quickTapStillReleases() {
        d.onEdge(true, 50);
        now.set(15); assertTrue(d.onEdge(false, 50).isEmpty());
        now.set(50); assertEquals(List.of(false), d.onTimer());
    }

    @Test void zeroWindowPassesEverything() {
        assertEquals(List.of(true), d.onEdge(true, 0));
        assertEquals(List.of(false), d.onEdge(false, 0));
    }

    @Test void timerBeforeTheDeadlineEmitsNothing() {
        d.onEdge(true, 50);
        now.set(15); d.onEdge(false, 50);
        now.set(30); assertTrue(d.onTimer().isEmpty());
        assertEquals(50, d.nextDeadline());
    }

    @Test void edgeAfterTheWindowPassesAtOnce() {
        d.onEdge(true, 50);
        now.set(80); assertEquals(List.of(false), d.onEdge(false, 50));
    }

    @Test void noDeadlineWhenIdle() {
        assertTrue(d.nextDeadline() < 0);
        d.onEdge(true, 50);
        now.set(60); d.onTimer();
        assertTrue(d.nextDeadline() < 0);
    }
}
