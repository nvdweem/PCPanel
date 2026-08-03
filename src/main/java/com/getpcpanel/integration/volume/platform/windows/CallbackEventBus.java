package com.getpcpanel.integration.volume.platform.windows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.getpcpanel.util.concurrent.AppThreads;

import jakarta.enterprise.event.Event;
import lombok.extern.log4j.Log4j2;

/**
 * Publishes the events raised from {@code SndCtrl.dll}'s callbacks on one background thread instead of
 * on the thread the DLL called us back on.
 *
 * <p>The DLL reports device and session arrivals from the threads the Windows audio API owns, and it
 * makes those calls <b>while holding its global {@code g_audioMutex}</b> — the same lock every volume,
 * mute and default-device call has to take. Delivering a CDI event there runs arbitrary observers under
 * that lock: a mute-colour recompute, hardware lighting writes, a websocket push to the browser. Any one
 * of them that blocks strands the lock, and because every dial and button is executed by the single
 * command thread, the first volume action to queue behind it takes all hardware control down with it —
 * silently, and until the application is restarted. Windows documents these notification callbacks as
 * unsafe to block in for the same reason.
 *
 * <p>So the callbacks do their bookkeeping and return, and observers run here. One thread, so events are
 * still delivered in the order they were fired; a daemon thread, so it never holds up shutdown. An
 * observer that throws is logged, and the next event is still delivered.
 *
 * <p>Only the callbacks the DLL makes under that lock need this — {@code deviceAdded},
 * {@code deviceRemoved}, {@code addSession} and {@code removeSession}. The session volume/mute/name
 * notifications are raised without it and stay synchronous.
 */
@Log4j2
class CallbackEventBus {
    private final Event<Object> delegate;
    private final ExecutorService executor;

    CallbackEventBus(Event<Object> delegate) {
        this.delegate = delegate;
        executor = Executors.newSingleThreadExecutor(AppThreads.factory("audio-events", true));
    }

    /** Queues {@code event} for delivery and returns; never runs an observer on the caller. */
    void fire(Object event) {
        try {
            executor.execute(() -> deliver(event));
        } catch (Exception e) {
            // Only reachable once the executor is shutting down; losing a cosmetic update then is fine.
            log.debug("Dropped audio event {}", event, e);
        }
    }

    /** Fires on {@code bus} when there is one, so the JNI callbacks work outside a running app too. */
    static void fire(@Nullable CallbackEventBus bus, Object event) {
        if (bus != null) {
            bus.fire(event);
        }
    }

    private void deliver(Object event) {
        try {
            delegate.fire(event);
        } catch (Throwable t) {
            log.warn("Observer failed handling audio event {}", event, t);
        }
    }
}
