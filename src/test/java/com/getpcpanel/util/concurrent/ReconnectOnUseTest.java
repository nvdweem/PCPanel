package com.getpcpanel.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ReconnectOnUseTest {
    private static final long COOLDOWN = 5_000;

    /** Counts attempts and lets a test hold one in flight until it releases it. */
    private static final class Attempts implements Runnable {
        final AtomicInteger started = new AtomicInteger();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile CountDownLatch release = new CountDownLatch(0);

        @Override
        public void run() {
            started.incrementAndGet();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.countDown();
        }

        void awaitFinished() throws InterruptedException {
            assertTrue(finished.await(5, TimeUnit.SECONDS), "the attempt never finished");
        }
    }

    @Test
    void aRequestWhileAvailableStartsNothing() {
        var attempts = new Attempts();
        var reconnect = new ReconnectOnUse("test", COOLDOWN, () -> false, attempts);

        assertFalse(reconnect.request(1_000));
        assertEquals(0, attempts.started.get());
    }

    @Test
    void theFirstRequestWhileUnavailableStartsAnAttempt() throws InterruptedException {
        var attempts = new Attempts();
        var reconnect = new ReconnectOnUse("test", COOLDOWN, () -> true, attempts);

        assertTrue(reconnect.request(1_000));
        attempts.awaitFinished();
        assertEquals(1, attempts.started.get());
    }

    @Test
    void aSweepOfRequestsStartsOneAttempt() throws InterruptedException {
        var attempts = new Attempts();
        var reconnect = new ReconnectOnUse("test", COOLDOWN, () -> true, attempts);

        var startedByRequest = 0;
        for (var tick = 0; tick < 100; tick++) {
            if (reconnect.request(1_000 + tick * 20L)) {
                startedByRequest++;
            }
        }
        attempts.awaitFinished();

        assertEquals(1, startedByRequest);
        assertEquals(1, attempts.started.get());
    }

    @Test
    void aRequestAfterTheCooldownStartsAnotherAttempt() throws InterruptedException {
        var attempts = new Attempts();
        var reconnect = new ReconnectOnUse("test", COOLDOWN, () -> true, attempts);

        assertTrue(reconnect.request(1_000));
        attempts.awaitFinished();

        assertFalse(reconnect.request(1_000 + COOLDOWN - 1));
        assertTrue(reconnect.request(1_000 + COOLDOWN));
    }

    @Test
    void anAttemptStillRunningBlocksTheNextOneEvenAfterTheCooldown() throws InterruptedException {
        var attempts = new Attempts();
        attempts.release = new CountDownLatch(1);
        var reconnect = new ReconnectOnUse("test", COOLDOWN, () -> true, attempts);

        assertTrue(reconnect.request(1_000));
        assertFalse(reconnect.request(1_000 + COOLDOWN * 10), "an attempt is still in flight");

        attempts.release.countDown();
        attempts.awaitFinished();
    }
}
