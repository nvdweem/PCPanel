package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
}
