package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Guards the second half of the audio deadlock rule: <b>an observer must never run on the thread the
 * DLL called us back on.</b>
 *
 * <p>{@code SndCtrl.dll} calls {@code deviceAdded} / {@code addSession} / {@code removeSession} while
 * holding its global {@code g_audioMutex}, which every volume, mute and default-device call has to
 * take. Delivering the CDI event on that thread runs arbitrary observers under that lock — mute-colour
 * recomputes, hardware lighting writes, a blocking websocket push to the browser. One that blocks
 * strands the lock, and since all dials and buttons run on the single command thread, the next volume
 * action queues behind it and takes every control on every device with it until a restart.
 *
 * @see SndCtrlNativeCallLockTest for the other direction (never enter the DLL holding {@code devices})
 */
class CallbackEventBusTest {
    @Test
    void fireReturnsWhileAnObserverIsStillBlocked() throws Exception {
        var observerEntered = new CountDownLatch(1);
        var releaseObserver = new CountDownLatch(1);
        var bus = new CallbackEventBus(stubEvent(event -> {
            observerEntered.countDown();
            await(releaseObserver);
        }));

        bus.fire("first");

        // The callback thread is what matters: it must be back inside the DLL — and out of g_audioMutex
        // — while the observer is still stuck. Waiting for the observer to actually be running first
        // makes this a real test of the handoff rather than of a race we happened to win.
        assertTrue(observerEntered.await(5, TimeUnit.SECONDS), "the observer never ran, so this proves nothing");
        bus.fire("second"); // Would block here if delivery were synchronous.
        releaseObserver.countDown();
    }

    @Test
    void eventsAreDeliveredInTheOrderTheyWereFired() throws Exception {
        var delivered = new ConcurrentLinkedQueue<Object>();
        var allSeen = new CountDownLatch(3);
        var bus = new CallbackEventBus(stubEvent(event -> {
            delivered.add(event);
            allSeen.countDown();
        }));

        bus.fire("one");
        bus.fire("two");
        bus.fire("three");

        assertTrue(allSeen.await(5, TimeUnit.SECONDS), "not every event was delivered");
        assertEquals(List.of("one", "two", "three"), List.copyOf(delivered));
    }

    @Test
    void anObserverThatThrowsDoesNotStopLaterEvents() throws Exception {
        var delivered = new ConcurrentLinkedQueue<Object>();
        var allSeen = new CountDownLatch(2);
        var bus = new CallbackEventBus(stubEvent(event -> {
            if ("boom".equals(event)) {
                throw new IllegalStateException("observer blew up");
            }
            delivered.add(event);
            allSeen.countDown();
        }));

        bus.fire("before");
        bus.fire("boom");
        bus.fire("after");

        assertTrue(allSeen.await(5, TimeUnit.SECONDS), "a throwing observer killed the delivery thread");
        assertEquals(List.of("before", "after"), List.copyOf(delivered));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Minimal {@link Event} whose {@code fire} runs the given observer synchronously, as CDI does.
     * Anonymous on purpose: a <em>named</em> concrete {@link Event} anywhere in the project would make
     * {@code RestDtoSerializationSmokeTest} start building {@code AudioDevice} instances through its
     * constructor, which leaves the fluent-set {@code dataflow} null and fails on serialization.
     */
    private static Event<Object> stubEvent(Consumer<Object> observer) {
        return new Event<>() {
            @Override
            public void fire(Object event) {
                observer.accept(event);
            }

            @Override
            public <U> CompletionStage<U> fireAsync(U event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Event<Object> select(Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
