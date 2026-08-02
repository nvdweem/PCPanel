package com.getpcpanel.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadDumpCollector (a wedged thread is identifiable from the bundle alone)")
class ThreadDumpCollectorTest {
    private static final long TIMEOUT_MS = 5_000;

    @Test
    @DisplayName("every live thread is named with its state and its stack")
    void namesEveryThreadWithItsStack() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thread = new Thread(() -> await(arrived, release), "test-parked-thread");
        thread.setDaemon(true);
        thread.start();
        assertTrue(arrived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the thread should have started");

        try {
            var dump = new ThreadDumpCollector().collect();

            assertTrue(dump.contains("\"test-parked-thread\""), () -> "the thread should be named:\n" + dump);
            assertTrue(dump.contains("ThreadDumpCollectorTest.await"), () -> "its stack should be present:\n" + dump);
        } finally {
            release.countDown();
            thread.join(TIMEOUT_MS);
        }
    }

    @Test
    @DisplayName("a thread blocked on a monitor names the thread holding it")
    void namesTheHolderOfAContendedMonitor() throws Exception {
        var lock = new Object();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        var holder = new Thread(() -> {
            synchronized (lock) {
                held.countDown();
                await(new CountDownLatch(0), release);
            }
        }, "test-lock-holder");
        holder.setDaemon(true);
        holder.start();
        assertTrue(held.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the holder should have taken the lock");

        var blocked = new Thread(() -> {
            synchronized (lock) {
                // Entering is the whole point; there is nothing to do once inside.
            }
        }, "test-lock-waiter");
        blocked.setDaemon(true);
        blocked.start();
        waitForState(blocked, Thread.State.BLOCKED);

        try {
            var dump = new ThreadDumpCollector().collect();

            assertTrue(dump.contains("\"test-lock-waiter\""), () -> "the blocked thread should be named:\n" + dump);
            assertTrue(dump.contains("BLOCKED"), () -> "its state should be reported:\n" + dump);
            assertTrue(dump.contains("held by \"test-lock-holder\""), () -> "the holder should be named:\n" + dump);
        } finally {
            release.countDown();
            holder.join(TIMEOUT_MS);
            blocked.join(TIMEOUT_MS);
        }
    }

    @Test
    @DisplayName("a thread inside wait() does not read as contending for the monitor it released")
    void separatesWaitingFromContending() throws Exception {
        var lock = new Object();
        var inside = new CountDownLatch(1);

        var waiter = new Thread(() -> {
            synchronized (lock) {
                inside.countDown();
                try {
                    lock.wait(TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-monitor-waiter");
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(inside.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the waiter should have entered wait()");
        waitForState(waiter, Thread.State.TIMED_WAITING);

        try {
            var section = sectionFor(new ThreadDumpCollector().collect(), "test-monitor-waiter");

            assertTrue(section.contains("- waiting on <"), () -> "an idle wait should be reported as such:\n" + section);
            assertFalse(section.contains("waiting to lock"), () -> "wait() released the monitor, so this is not contention:\n" + section);
            assertFalse(section.contains("held by"), () -> "a released monitor has no owner to name:\n" + section);
        } finally {
            synchronized (lock) {
                lock.notifyAll();
            }
            waiter.join(TIMEOUT_MS);
        }
    }

    @Test
    @DisplayName("a runaway stack is truncated rather than filling the bundle")
    void boundsADeepStack() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var depth = ThreadDumpCollector.MAX_FRAMES + 50;
        var thread = new Thread(() -> recurse(depth, arrived, release), "test-deep-thread");
        thread.setDaemon(true);
        thread.start();
        assertTrue(arrived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the thread should have recursed");

        try {
            var dump = new ThreadDumpCollector().collect();
            var section = sectionFor(dump, "test-deep-thread");

            var frames = section.lines().filter(l -> l.startsWith("\tat ")).count();
            assertTrue(frames <= ThreadDumpCollector.MAX_FRAMES,
                    () -> "expected at most " + ThreadDumpCollector.MAX_FRAMES + " frames but got " + frames);
            assertTrue(section.contains("more frames"), () -> "the truncation should be visible:\n" + section);
        } finally {
            release.countDown();
            thread.join(TIMEOUT_MS);
        }
    }

    @Test
    @DisplayName("threads are ordered by name so two dumps can be compared")
    void ordersThreadsByName() {
        var dump = new ThreadDumpCollector().collect();

        var names = Pattern.compile("^\"(.*)\" id=", Pattern.MULTILINE)
                           .matcher(dump)
                           .results()
                           .map(r -> r.group(1))
                           .toList();

        assertFalse(names.isEmpty(), () -> "the dump should contain threads:\n" + dump);
        assertEquals(names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), names, "threads should be sorted by name");
    }

    /** The text from one thread's header up to the next, so a per-thread assertion cannot match another's frames. */
    private static String sectionFor(String dump, String threadName) {
        var start = dump.indexOf('"' + threadName + '"');
        assertTrue(start >= 0, () -> threadName + " is missing from:\n" + dump);
        var next = dump.indexOf("\n\"", start);
        return next < 0 ? dump.substring(start) : dump.substring(start, next);
    }

    private static void waitForState(Thread thread, Thread.State state) {
        var deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (thread.getState() != state && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(state, thread.getState(), () -> thread.getName() + " never reached " + state);
    }

    @SuppressWarnings("SameParameterValue")
    private static void recurse(int depth, CountDownLatch arrived, CountDownLatch release) {
        if (depth > 0) {
            recurse(depth - 1, arrived, release);
            return;
        }
        await(arrived, release);
    }

    private static void await(CountDownLatch arrived, CountDownLatch release) {
        arrived.countDown();
        try {
            release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
