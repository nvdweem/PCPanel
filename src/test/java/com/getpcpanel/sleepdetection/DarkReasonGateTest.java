package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.sleepdetection.DarkReasonGate.Reason;

class DarkReasonGateTest {
    // The project runs tests per-class (see junit-platform.properties), so rebuild state every method.
    private List<String> events;
    private DarkReasonGate gate;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        gate = new DarkReasonGate(() -> events.add("dark"), () -> events.add("light"));
    }

    @Test
    void singleReasonGoesDarkThenRelights() {
        gate.add(Reason.display);
        gate.clear(Reason.display);
        assertEquals(List.of("dark", "light"), events);
    }

    @Test
    void overlappingReasonsDarkenOnceAndRelightOnceAllClear() {
        gate.add(Reason.lock);     // dark
        gate.add(Reason.display);  // already dark, no event
        gate.clear(Reason.display); // still locked, stay dark
        gate.clear(Reason.lock);    // last reason gone -> light
        assertEquals(List.of("dark", "light"), events);
    }

    @Test
    void monitorOnWhileStillLockedDoesNotRelight() {
        gate.add(Reason.lock);
        gate.add(Reason.display);
        gate.clear(Reason.display); // monitor came back but still locked
        assertEquals(List.of("dark"), events);
    }

    @Test
    void duplicateAddsAndClearsAreIdempotent() {
        gate.add(Reason.lock);
        gate.add(Reason.lock);   // no second dark
        gate.clear(Reason.lock);
        gate.clear(Reason.lock); // no second light
        assertEquals(List.of("dark", "light"), events);
    }

    @Test
    void clearingAnUnknownReasonDoesNothing() {
        gate.clear(Reason.display);
        assertEquals(List.of(), events);
    }

    @Test
    void resetRelightsRegardlessOfReasons() {
        gate.add(Reason.lock);
        gate.add(Reason.display);
        gate.reset();
        assertEquals(List.of("dark", "light"), events);
        // After a reset every reason is cleared, so a fresh reason darkens again.
        gate.add(Reason.suspend);
        assertEquals(List.of("dark", "light", "dark"), events);
    }

    @Test
    void resumeOnWindowsWithoutPriorSuspendStillRelights() {
        // Windows can't fire goingToSuspend, so only the watchdog's resume arrives.
        gate.reset();
        assertEquals(List.of("light"), events);
    }
}
