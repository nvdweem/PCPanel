package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MacPowerNotificationsTest {
    private static final class FakeBackend implements MacPowerNotifications.Backend {
        final CountDownLatch registerGate = new CountDownLatch(1);
        final CountDownLatch tornDown = new CountDownLatch(1);
        final AtomicInteger loopRuns = new AtomicInteger();
        final AtomicInteger teardowns = new AtomicInteger();
        private final CountDownLatch stop = new CountDownLatch(1);

        @Override
        public boolean register(MacPowerMessages messages) {
            try {
                registerGate.await();
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }

        @Override
        public void runLoop() {
            loopRuns.incrementAndGet();
            try {
                stop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stopLoop() {
            stop.countDown();
        }

        @Override
        public void teardown() {
            teardowns.incrementAndGet();
            tornDown.countDown();
        }

        @Override
        public void allowPowerChange(long notificationId) {
        }
    }

    @Test
    void startsRunsAndTearsDownOnClose() throws Exception {
        var backend = new FakeBackend();
        backend.registerGate.countDown();
        var n = new MacPowerNotifications(backend, Duration.ofSeconds(2), t -> { }, () -> { });
        assertTrue(n.start());
        assertTrue(n.isRunning());
        n.close();
        assertTrue(backend.tornDown.await(1, TimeUnit.SECONDS));
        assertEquals(1, backend.teardowns.get());
        assertFalse(n.isRunning());
    }

    @Test
    void unplannedStopIsReportedButCloseIsNot() throws Exception {
        var backend = new FakeBackend();
        backend.registerGate.countDown();
        var stopped = new CountDownLatch(1);
        var n = new MacPowerNotifications(backend, Duration.ofSeconds(2), t -> { }, stopped::countDown);
        assertTrue(n.start());
        backend.stopLoop();
        assertTrue(stopped.await(1, TimeUnit.SECONDS));
        assertEquals(1, backend.teardowns.get());
        n.close();

        var planned = new FakeBackend();
        planned.registerGate.countDown();
        var closedStopped = new AtomicInteger();
        var m = new MacPowerNotifications(planned, Duration.ofSeconds(2), t -> { }, closedStopped::incrementAndGet);
        assertTrue(m.start());
        m.close();
        assertEquals(0, closedStopped.get());
    }

    @Test
    void closeKeepsTheInterruptFlagAfterWaitingForTeardown() {
        var backend = new FakeBackend();
        backend.registerGate.countDown();
        var n = new MacPowerNotifications(backend, Duration.ofSeconds(2), t -> { }, () -> { });
        assertTrue(n.start());
        Thread.currentThread().interrupt();
        try {
            n.close();
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, backend.teardowns.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void startThatTimesOutTearsItselfDownWhenRegistrationFinishesLater() throws Exception {
        var backend = new FakeBackend();
        var n = new MacPowerNotifications(backend, Duration.ofMillis(50), t -> { }, () -> { });
        assertFalse(n.start());
        n.close();
        backend.registerGate.countDown();
        assertTrue(backend.tornDown.await(2, TimeUnit.SECONDS));
        assertEquals(0, backend.loopRuns.get());
        assertEquals(1, backend.teardowns.get());
    }

    @Test
    void interruptedStartTearsItselfDownWhenRegistrationFinishesLater() throws Exception {
        var backend = new FakeBackend();
        var n = new MacPowerNotifications(backend, Duration.ofSeconds(5), t -> { }, () -> { });
        Thread.currentThread().interrupt();
        try {
            assertFalse(n.start());
            assertTrue(Thread.interrupted(), "interrupt status must be kept");
        } finally {
            Thread.interrupted();
        }
        backend.registerGate.countDown();
        assertTrue(backend.tornDown.await(2, TimeUnit.SECONDS));
        assertEquals(0, backend.loopRuns.get());
        assertEquals(1, backend.teardowns.get());
    }
}
