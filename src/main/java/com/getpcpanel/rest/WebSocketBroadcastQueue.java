package com.getpcpanel.rest;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.getpcpanel.util.concurrent.AppThreads;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Carries UI updates from the threads that produce them to the browser, so that producing an event and
 * delivering it are never the same thread's problem.
 *
 * <p>Events are fired from wherever they happen: the HID input thread, the command thread, and the
 * Windows audio backend's own notification threads — which call up into Java holding the lock every
 * volume action needs. Sending on those threads means the slowest websocket client decides how long
 * they are held, and a client that has stopped reading (a browser tab that did not survive a
 * sleep/resume, say) never releases them at all. That is how a cosmetic UI update ends up freezing
 * every dial and button on every device.
 *
 * <p>So producers only hand over a finished frame and carry on. One dispatcher thread does the sending,
 * which keeps frames in the order they were produced without a producer ever waiting on a socket. The
 * queue is bounded and drops its oldest frames when a client falls behind: this is a state feed, so the
 * newest frames are the ones worth keeping, and unbounded buffering would only trade a freeze for
 * memory growth.
 */
@Log4j2
@ApplicationScoped
class WebSocketBroadcastQueue {
    /** Sized for a burst — knob ticks arrive as fast as the hardware reports them. */
    static final int CAPACITY = 256;
    /** A client that cannot take a frame within this is not worth holding every other client up for. */
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final BlockingQueue<String> pending = new ArrayBlockingQueue<>(CAPACITY);
    private final Consumer<String> sender;
    @Nullable private volatile Thread dispatcher;
    private volatile boolean running;

    WebSocketBroadcastQueue() {
        this(json -> EventWebSocket.sendToAll(json, SEND_TIMEOUT));
    }

    WebSocketBroadcastQueue(Consumer<String> sender) {
        this.sender = sender;
    }

    @PostConstruct
    void start() {
        running = true;
        var thread = AppThreads.named("ws-broadcast", true, this::run);
        dispatcher = thread;
        thread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        var thread = dispatcher;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Hands {@code json} to the dispatcher. Returns immediately, whatever the clients are doing. */
    void enqueue(String json) {
        while (!pending.offer(json)) {
            var dropped = pending.poll();
            if (dropped == null) {
                return; // Drained underneath us; the next offer would succeed, but so would giving up.
            }
            log.debug("WebSocket client is not keeping up; dropped the oldest queued event");
        }
    }

    private void run() {
        while (running) {
            String json;
            try {
                json = pending.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                sender.accept(json);
            } catch (Throwable t) {
                // Delivery is best-effort: one bad frame or client must not end the dispatcher, or the
                // UI would silently stop updating for the rest of the run.
                log.debug("Failed to dispatch event to WebSocket clients", t);
            }
        }
    }

    /** Test seam: how many frames are waiting. */
    int queued() {
        return pending.size();
    }
}
