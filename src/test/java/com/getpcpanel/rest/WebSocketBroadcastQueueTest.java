package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Guards what the websocket path must never do again: <b>make a producer wait on a client.</b>
 *
 * <p>Events are broadcast from the HID input thread, the command thread and the Windows audio
 * backend's notification threads — the last of which hold the lock every volume action needs. A send
 * that waits for the socket therefore lets a browser tab that stopped reading freeze all hardware
 * control, which is exactly what a sleep/resume cycle can produce.
 */
class WebSocketBroadcastQueueTest {
    private WebSocketBroadcastQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.stop();
        }
    }

    @Test
    void enqueueReturnsWhileAClientIsStillStalled() throws Exception {
        var sendEntered = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        queue = started(json -> {
            sendEntered.countDown();
            await(releaseSend);
        });

        queue.enqueue("first");
        assertTrue(sendEntered.await(5, TimeUnit.SECONDS), "the send never started, so this proves nothing");

        // The producer's guarantee: handing over a frame completes even though the client is stuck.
        queue.enqueue("second");
        releaseSend.countDown();
    }

    @Test
    void framesAreSentInTheOrderTheyWereQueued() throws Exception {
        var sent = new ConcurrentLinkedQueue<String>();
        var allSeen = new CountDownLatch(3);
        queue = started(json -> {
            sent.add(json);
            allSeen.countDown();
        });

        queue.enqueue("one");
        queue.enqueue("two");
        queue.enqueue("three");

        assertTrue(allSeen.await(5, TimeUnit.SECONDS), "not every frame was sent");
        assertEquals(List.of("one", "two", "three"), List.copyOf(sent));
    }

    @Test
    void aStalledClientMakesTheQueueDropOldFramesRatherThanGrow() throws Exception {
        var releaseSend = new CountDownLatch(1);
        var sendEntered = new CountDownLatch(1);
        queue = started(json -> {
            sendEntered.countDown();
            await(releaseSend);
        });
        // Park the dispatcher inside a send first, so everything after it has to queue up.
        queue.enqueue("stalls-the-dispatcher");
        assertTrue(sendEntered.await(5, TimeUnit.SECONDS), "the send never started, so nothing would queue");

        // Far more than the queue holds: a knob turned against a client that stopped reading.
        for (var i = 0; i < WebSocketBroadcastQueue.CAPACITY * 4; i++) {
            queue.enqueue("frame-" + i);
        }

        assertTrue(queue.queued() <= WebSocketBroadcastQueue.CAPACITY,
                "queue grew past its bound (" + queue.queued() + "), trading a freeze for memory growth");
        releaseSend.countDown();
    }

    @Test
    void aFailingSendDoesNotStopLaterFrames() throws Exception {
        var sent = new ConcurrentLinkedQueue<String>();
        var allSeen = new CountDownLatch(2);
        queue = started(json -> {
            if ("boom".equals(json)) {
                throw new IllegalStateException("send blew up");
            }
            sent.add(json);
            allSeen.countDown();
        });

        queue.enqueue("before");
        queue.enqueue("boom");
        queue.enqueue("after");

        assertTrue(allSeen.await(5, TimeUnit.SECONDS), "a failing send killed the dispatcher");
        assertEquals(List.of("before", "after"), List.copyOf(sent));
    }

    @Test
    void stopEndsTheDispatcher() throws Exception {
        var sent = new ConcurrentLinkedQueue<String>();
        queue = started(sent::add);

        queue.stop();
        Thread.sleep(100);
        queue.enqueue("after-stop");
        Thread.sleep(100);

        assertFalse(sent.contains("after-stop"), "the dispatcher kept running after stop()");
    }

    private static WebSocketBroadcastQueue started(java.util.function.Consumer<String> sender) {
        var queue = new WebSocketBroadcastQueue(sender);
        queue.start();
        return queue;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
