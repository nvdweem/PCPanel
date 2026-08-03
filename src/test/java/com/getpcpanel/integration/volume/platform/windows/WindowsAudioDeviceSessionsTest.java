package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * The session map is written by the audio backend's notification threads and read by the command thread
 * and by REST requests. Those callbacks used to be serialized by the DLL's global audio lock, which is
 * held across them no longer — so the map has to hold its own shape.
 */
class WindowsAudioDeviceSessionsTest {
    private static final int PID = 1234;

    @Test
    void aSessionSurvivesUntilItsLastPointerIsGone() {
        var device = device();

        device.addSession(1L, PID, "chrome.exe", "Chrome", null, 1f, false);
        device.addSession(2L, PID, "chrome.exe", "Chrome", null, 1f, false);
        device.removeSession(1L, PID);

        assertNotNull(device.getSessions().get(PID), "the session still has a live pointer and must remain");

        device.removeSession(2L, PID);
        assertEquals(0, device.getSessions().size(), "the last pointer went away, so the session should be gone");
    }

    @Test
    void concurrentAddAndRemoveOnOnePidLeaveNothingBehind() throws Exception {
        var device = device();
        var threads = 8;
        var iterations = 300;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var failure = new AtomicReference<Throwable>();
        var workers = new ArrayList<Thread>();

        for (var t = 0; t < threads; t++) {
            var pointer = t + 1L; // its own pointer, deliberately the same pid: maximum contention
            var worker = new Thread(() -> {
                try {
                    start.await();
                    for (var i = 0; i < iterations; i++) {
                        device.addSession(pointer, PID, "chrome.exe", "Chrome", null, 1f, false);
                        device.removeSession(pointer, PID);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            }, "session-churn-" + t);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "the churn did not finish; a lock is held too long");
        workers.forEach(Thread::interrupt);

        assertEquals(null, failure.get(), "concurrent session churn threw: " + failure.get());
        assertEquals(0, device.getSessions().size(),
                "every pointer was removed, so no session should be left — an interleaved add/remove orphaned one");
    }

    /** No buses: {@code publish} becomes a no-op, leaving just the bookkeeping under test. */
    private static WindowsAudioDevice device() {
        return new WindowsAudioDevice(null, null, "Speakers", "device-1");
    }
}
