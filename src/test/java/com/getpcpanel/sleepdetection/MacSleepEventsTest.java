package com.getpcpanel.sleepdetection;

import static com.getpcpanel.sleepdetection.SystemEventType.goingToSuspend;
import static com.getpcpanel.sleepdetection.SystemEventType.resumedFromSuspend;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MacSleepEventsTest {
    private List<SystemEventType> delivered;
    private MacSleepEvents events;

    @BeforeEach
    void setUp() {
        delivered = Collections.synchronizedList(new ArrayList<>());
        events = new MacSleepEvents(delivered::add);
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    @Test
    void suspendThenWatchdogResumeDeliversBothOnce() throws Exception {
        events.fromIoKit(goingToSuspend);
        events.fromWatchdog(resumedFromSuspend);
        events.fromIoKit(resumedFromSuspend);
        events.flush();
        assertEquals(List.of(goingToSuspend, resumedFromSuspend), delivered);
    }

    @Test
    void ioKitResumeThenWatchdogResumeDeliversOnce() throws Exception {
        events.fromIoKit(goingToSuspend);
        events.fromIoKit(resumedFromSuspend);
        events.fromWatchdog(resumedFromSuspend);
        events.flush();
        assertEquals(List.of(goingToSuspend, resumedFromSuspend), delivered);
    }

    @Test
    void watchdogResumeThenANewIoKitSleepAreAllDelivered() throws Exception {
        events.fromWatchdog(resumedFromSuspend);
        events.fromIoKit(goingToSuspend);
        events.fromIoKit(resumedFromSuspend);
        events.flush();
        assertEquals(List.of(resumedFromSuspend, goingToSuspend, resumedFromSuspend), delivered);
    }

    @Test
    void watchdogResumeOfASecondSleepIsDelivered() throws Exception {
        events.fromIoKit(goingToSuspend);
        events.fromIoKit(resumedFromSuspend);
        events.fromIoKit(goingToSuspend);
        events.fromWatchdog(resumedFromSuspend);
        events.flush();
        assertEquals(List.of(goingToSuspend, resumedFromSuspend, goingToSuspend, resumedFromSuspend), delivered);
    }

    @Test
    void watchdogAloneDeliversEveryResume() throws Exception {
        events.fromWatchdog(resumedFromSuspend);
        events.fromWatchdog(resumedFromSuspend);
        events.flush();
        assertEquals(2, delivered.size());
    }

    @Test
    void watchdogResumesPassAgainOnceIoKitStopped() throws Exception {
        events.fromIoKit(goingToSuspend);
        events.fromIoKit(resumedFromSuspend);
        events.ioKitStopped();
        events.fromWatchdog(resumedFromSuspend);
        events.fromWatchdog(resumedFromSuspend);
        events.flush();
        assertEquals(List.of(goingToSuspend, resumedFromSuspend, resumedFromSuspend, resumedFromSuspend), delivered);
    }

    @Test
    void concurrentSourcesDeliverOneResumePerGeneration() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            for (var round = 0; round < 100; round++) {
                delivered.clear();
                events.fromIoKit(goingToSuspend);
                events.flush();
                var go = new CountDownLatch(1);
                var done = new CountDownLatch(8);
                for (var i = 0; i < 8; i++) {
                    var ioKit = i % 2 == 0;
                    pool.execute(() -> {
                        try {
                            go.await();
                            if (ioKit) {
                                events.fromIoKit(resumedFromSuspend);
                            } else {
                                events.fromWatchdog(resumedFromSuspend);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                go.countDown();
                assertTrue(done.await(5, TimeUnit.SECONDS));
                events.flush();
                assertEquals(List.of(goingToSuspend, resumedFromSuspend), delivered);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nothingIsDeliveredAfterClose() throws Exception {
        events.close();
        events.fromIoKit(goingToSuspend);
        events.fromWatchdog(resumedFromSuspend);
        assertEquals(List.of(), delivered);
    }

    @Test
    void eventsQueuedBehindABlockedTaskAreDroppedByClose() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var local = new MacSleepEvents(delivered::add, executor, Duration.ofMillis(50));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        local.fromIoKit(goingToSuspend);
        local.close();
        release.countDown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(List.of(), delivered);
    }
}
