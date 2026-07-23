package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        gate = new DarkReasonGate(() -> events.add("dark"), () -> events.add("light"), Runnable::run);
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

    /** Switching sleep detection off while dark relights; the reasons are gone so nothing re-darkens. */
    @Test
    void resetIfDarkRelightsOnlyWhenSomethingWasDark() {
        gate.resetIfDark(); // lit and no reasons: a settings save must not send a gratuitous relight
        assertEquals(List.of(), events);
        gate.add(Reason.lock);
        gate.resetIfDark();
        assertEquals(List.of("dark", "light"), events);
        gate.resetIfDark(); // idempotent once cleared
        assertEquals(List.of("dark", "light"), events);
    }

    @Test
    void resumeOnWindowsWithoutPriorSuspendStillRelights() {
        // Windows can't fire goingToSuspend, so only the watchdog's resume arrives.
        gate.reset();
        assertEquals(List.of("light"), events);
    }

    /**
     * The boot-only remainder of #145: a quick dark→light pair (display off/on blink during logon, a
     * lock-inference blip) must execute its actions in decision order even when the off action itself
     * is slow. The off used to run on a freshly spawned thread while the relight ran synchronously on
     * the caller's thread, so the ALL_OFF could land on the device queue after the relight — panels
     * dark until the user touched a lighting setting. A single-threaded executor makes the order
     * structural: the relight cannot start before the off has finished.
     */
    @Test
    void slowOffActionCannotOvertakeTheMatchingRelight() throws InterruptedException {
        var order = Collections.synchronizedList(new ArrayList<String>());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var slowGate = new DarkReasonGate(() -> {
                try {
                    Thread.sleep(150); // the spawned-thread scheduling delay that inverted the order at boot
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add("dark");
            }, () -> order.add("light"), executor);

            slowGate.add(Reason.display);   // boot-time display blink: off...
            slowGate.clear(Reason.display); // ...and on again a moment later
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "gate actions did not finish");
            assertEquals(List.of("dark", "light"), order);
        } finally {
            executor.shutdownNow();
        }
    }
}
