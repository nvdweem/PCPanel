package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MacPowerMessagesTest {
    private List<String> log;
    private MacPowerMessages messages;

    @BeforeEach
    void setUp() {
        log = new ArrayList<>();
        messages = new MacPowerMessages(t -> log.add(t.name()), id -> log.add("allow " + id), Runnable::run);
    }

    @Test
    void constantsMatchIOMessageHeader() {
        assertEquals(0xE0000270, MacPowerMessages.CAN_SYSTEM_SLEEP);
        assertEquals(0xE0000280, MacPowerMessages.SYSTEM_WILL_SLEEP);
        assertEquals(0xE0000300, MacPowerMessages.SYSTEM_HAS_POWERED_ON);
    }

    @Test
    void canSleepIsAllowedWithoutAnEvent() {
        messages.handle(MacPowerMessages.CAN_SYSTEM_SLEEP, 7);
        assertEquals(List.of("allow 7"), log);
    }

    @Test
    void willSleepFiresThenAllows() {
        messages.handle(MacPowerMessages.SYSTEM_WILL_SLEEP, 9);
        assertEquals(List.of("goingToSuspend", "allow 9"), log);
    }

    @Test
    void willSleepQueuesTheSuspendBeforeAllowing() {
        // The answer may let the Mac sleep at once, so the suspend must already be queued ahead of any later resume.
        var m = new MacPowerMessages(t -> log.add(t.name()), id -> log.add("allow " + id), task -> log.add("queued"));
        m.handle(MacPowerMessages.SYSTEM_WILL_SLEEP, 4);
        assertEquals(List.of("queued", "allow 4"), log);
    }

    @Test
    void willSleepAllowsEvenWhenTheListenerFails() {
        var m = new MacPowerMessages(t -> { throw new IllegalStateException("boom"); }, id -> log.add("allow " + id), Runnable::run);
        m.handle(MacPowerMessages.SYSTEM_WILL_SLEEP, 3);
        assertEquals(List.of("allow 3"), log);
    }

    @Test
    void blockedListenerDoesNotDelayTheAck() throws Exception {
        var release = new CountDownLatch(1);
        var acked = new CountDownLatch(1);
        var order = Collections.synchronizedList(new ArrayList<String>());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var m = new MacPowerMessages(t -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add(t.name());
            }, id -> acked.countDown(), executor);
            m.handle(MacPowerMessages.SYSTEM_WILL_SLEEP, 1);
            assertTrue(acked.await(1, TimeUnit.SECONDS), "ack waited for the listener");
            m.handle(MacPowerMessages.SYSTEM_HAS_POWERED_ON, 0);
            assertEquals(List.of(), order);
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(List.of("goingToSuspend", "resumedFromSuspend"), order);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void poweredOnFiresResumeWithoutAllowing() {
        messages.handle(MacPowerMessages.SYSTEM_HAS_POWERED_ON, 0);
        assertEquals(List.of("resumedFromSuspend"), log);
    }

    @Test
    void otherMessagesAreIgnored() {
        messages.handle(0xE0000290, 1); // kIOMessageSystemWillNotSleep
        messages.handle(0xE0000320, 1); // kIOMessageSystemWillPowerOn
        assertEquals(List.of(), log);
    }

    @Test
    void failuresDoNotEscape() {
        var m = new MacPowerMessages(t -> { }, id -> { throw new IllegalStateException("boom"); }, Runnable::run);
        m.handle(MacPowerMessages.CAN_SYSTEM_SLEEP, 1);
    }
}
