package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import re.walk.sonar.SonarClient;
import re.walk.sonar.model.SonarChannel;
import re.walk.sonar.model.SonarMix;
import re.walk.sonar.model.SonarMode;
import re.walk.sonar.model.SonarRoute;
import re.walk.sonar.model.SonarState;
import org.junit.jupiter.api.Test;

import com.getpcpanel.util.concurrent.AppThreads;

/**
 * The flush thread in real time. The coalescing rules themselves are covered with a synthetic clock in
 * {@link SonarServiceWriteTest}; this proves that a queued write reaches Sonar without anything
 * calling {@link SonarService#flushDue(long)} by hand, well inside a second, and that a flush which
 * throws does not stop the ones after it. The bound is generous
 * for a slow machine, yet a scheduler that fires once a second would miss it most of the time.
 */
class SonarFlushThreadTest {
    private static final long DELIVERY_BOUND_MS = 900;

    private static class QueueClient extends SonarClient {
        final LinkedBlockingQueue<String> writes = new LinkedBlockingQueue<>();
        /** Set to make the next write throw, as a bug in the send path would. */
        volatile boolean failNext;

        QueueClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public boolean setVolume(SonarRoute route, double value) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated failure in the send path");
            }
            writes.add(route.volumePath(value));
            return true;
        }
    }

    @Test
    void aQueuedWriteIsSentByTheFlushThreadWithinAFractionOfASecond() throws Exception {
        var client = new QueueClient();
        var executor = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("sonar-flush-test", true));
        try {
            var service = SonarServiceFixtures.service(client, true, null, executor);
            service.replaceState(new SonarState(SonarMode.stream, Map.of()));

            for (var value : new double[] { 0.25, 0.75 }) {
                var start = System.nanoTime();
                service.setVolume(SonarChannel.Game, SonarMix.monitoring, value);
                var sent = client.writes.poll(DELIVERY_BOUND_MS, TimeUnit.MILLISECONDS);
                var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                var expected = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game).volumePath(value);
                assertTrue(expected.equals(sent),
                        () -> "expected the write of " + value + " within " + DELIVERY_BOUND_MS + " ms, got " + sent
                                + " after " + elapsedMs + " ms");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aFlushThatThrowsDoesNotStopLaterFlushes() throws Exception {
        var client = new QueueClient();
        client.failNext = true;
        var executor = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("sonar-flush-test", true));
        try {
            var service = SonarServiceFixtures.service(client, true, null, executor);
            service.replaceState(new SonarState(SonarMode.stream, Map.of()));

            service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.25);
            var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DELIVERY_BOUND_MS);
            while (client.failNext && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(!client.failNext, "precondition: the flush thread reached the failing write");

            service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.75);
            var sent = client.writes.poll(DELIVERY_BOUND_MS, TimeUnit.MILLISECONDS);

            var expected = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game).volumePath(0.75);
            assertTrue(expected.equals(sent), () -> "a throwing flush must not cancel later ones; got " + sent);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Holds each scheduled tick instead of running it, so a test runs them one at a time on its own thread.
     * Writes are stamped at time 0, so by the real clock a tick finds them due.
     */
    private static class ManualScheduler extends ScheduledThreadPoolExecutor {
        final Deque<Runnable> ticks = new ArrayDeque<>();
        final List<Long> delaysMs = new ArrayList<>();

        ManualScheduler() {
            super(1);
        }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            ticks.add(command);
            delaysMs.add(unit.toMillis(delay));
            return null;
        }

        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new AssertionError("the flush ticks by one-shot schedules, so it can stop when idle");
        }

        void runNextTick() {
            var tick = ticks.poll();
            assertNotNull(tick, "a tick should have been scheduled");
            tick.run();
        }
    }

    /** Records the route paths sent, and can run something inside a send, as a dial moving mid-flush would. */
    private static class HookClient extends QueueClient {
        @Nullable Runnable duringNextWrite;

        @Override public boolean setVolume(SonarRoute route, double value) {
            var hook = duringNextWrite;
            duringNextWrite = null;
            super.setVolume(route, value);
            if (hook != null) {
                hook.run();
            }
            return true;
        }
    }

    private static SonarService serviceOn(QueueClient client, ManualScheduler scheduler) {
        var service = SonarServiceFixtures.service(client, true, null, scheduler);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));
        return service;
    }

    private static String gamePath(double value) {
        return SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game).volumePath(value);
    }

    private static String chatPath(double value) {
        return SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Chat).volumePath(value);
    }

    /** Every interval from 40 ms (25/s) to 167 ms (6/s) is honoured to within one tick. */
    @Test
    void theFlushTicksEveryTenMilliseconds() {
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(new QueueClient(), scheduler);

            service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.5);
            scheduler.runNextTick(); // not due yet by the real clock, so it schedules the next tick

            assertEquals(List.of(10L, 10L), scheduler.delaysMs);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void writesQueuedWhileATickIsScheduledShareIt() {
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(new QueueClient(), scheduler);

            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.1, 0);
            service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, 0.2, 0);
            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.3, 0);

            assertEquals(1, scheduler.ticks.size());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void theTickerStopsWhenNothingIsQueuedAndTheNextWriteRestartsIt() {
        var client = new QueueClient();
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(client, scheduler);

            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.25, 0);
            scheduler.runNextTick();
            assertEquals(List.of(gamePath(0.25)), List.copyOf(client.writes));
            assertTrue(scheduler.ticks.isEmpty(), "nothing queued, so no further tick");

            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.75, 0);
            assertEquals(1, scheduler.ticks.size(), "the next write starts the ticker again");
            scheduler.runNextTick();

            assertEquals(List.of(gamePath(0.25), gamePath(0.75)), List.copyOf(client.writes));
            assertTrue(scheduler.ticks.isEmpty());
        } finally {
            scheduler.shutdownNow();
        }
    }

    /** The write lands while the tick that would otherwise stop is still running, so it must not be stranded. */
    @Test
    void aWriteQueuedDuringTheLastSendIsStillSent() {
        var client = new HookClient();
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(client, scheduler);
            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.25, 0);
            client.duringNextWrite = () -> service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, 0.5, 0);

            scheduler.runNextTick();
            assertEquals(1, scheduler.ticks.size(), "the write queued mid-tick keeps the ticker going");
            scheduler.runNextTick();

            assertEquals(List.of(gamePath(0.25), chatPath(0.5)), List.copyOf(client.writes));
            assertTrue(scheduler.ticks.isEmpty());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void aWriteNotYetDueKeepsTheTickerGoing() {
        var client = new QueueClient();
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(client, scheduler);

            service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.5); // due one interval from now
            scheduler.runNextTick();

            assertEquals(List.of(), List.copyOf(client.writes));
            assertEquals(1, scheduler.ticks.size());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void noTickIsScheduledAfterShutdown() {
        var scheduler = new ManualScheduler();
        try {
            var service = serviceOn(new QueueClient(), scheduler);
            service.onShutdown(null);

            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.25, 0);

            assertTrue(scheduler.ticks.isEmpty());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
