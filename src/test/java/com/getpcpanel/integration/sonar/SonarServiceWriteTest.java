package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

class SonarServiceWriteTest {
    private static class RecordingClient extends SonarClient {
        final List<String> writes = new ArrayList<>();
        @Nullable SonarMode mode = SonarMode.stream;
        SonarState served = new SonarState(SonarMode.stream, Map.of());
        /** Runs inside the first write, to stand in for a flush overlapping a slow HTTP call. */
        @Nullable Consumer<SonarRoute> duringFirstWrite;
        /** Runs inside every volume write, to stand in for a dial still moving while HTTP is on the wire. */
        @Nullable Consumer<SonarRoute> duringEachWrite;

        RecordingClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public Optional<SonarMode> fetchMode() {
            return Optional.ofNullable(mode);
        }

        @Override public Optional<SonarState> fetchState(SonarMode mode) {
            return Optional.of(served);
        }

        @Override public boolean setVolume(SonarRoute route, double value) {
            writes.add(route.volumePath(value));
            var each = duringEachWrite;
            if (each != null) {
                each.accept(route);
            }
            var hook = duringFirstWrite;
            duringFirstWrite = null;
            if (hook != null) {
                hook.accept(route);
            }
            return true;
        }

        @Override public boolean setMute(SonarRoute route, boolean muted) {
            writes.add(route.mutePath(muted));
            return true;
        }
    }

    /** The realistic starting point: a poll has read the levels before the dial moves. */
    private static SonarState streamWithGameLevel() {
        return new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game), new SonarLevel(0.5, false)));
    }

    @Test
    void aLocalWriteUpdatesTheCacheImmediately() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(streamWithGameLevel());

        service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.42);

        // No poll has run since the write; the LED must already see the new value.
        assertEquals(0.42, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void aBurstCollapsesToOneWritePerRouteAndKeepsTheLastValue() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.10, 1_000);
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.20, 1_010);
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.30, 1_020);
        service.flushDue(1_100);

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.3000"), client.writes);
    }

    @Test
    void classicModeCollapsesBothMixesOntoOneWrite() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.classic, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.40, 1_000);
        service.setVolumeAt(SonarChannel.Game, SonarMix.streaming, 0.40, 1_001);
        service.flushDue(1_100);

        assertEquals(1, client.writes.size());
        assertEquals("/volumeSettings/classic/game/Volume/0.4000", client.writes.get(0));
    }

    @Test
    void aPollDoesNotOverwriteARouteWrittenWithinThePrecedenceWindow() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(streamWithGameLevel());
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.90, 10_000);
        // A poll in flight since before the write returns the server's older value.
        service.applyPolledAt(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.10, false))), 10_500);

        assertEquals(0.90, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void aPollWinsOnceThePrecedenceWindowHasPassed() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(streamWithGameLevel());
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.90, 10_000);
        service.applyPolledAt(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.10, false))), 12_500);

        assertEquals(0.10, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void muteIsWrittenWithoutTouchingVolume() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Chat);
        service.replaceState(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.6, false))));

        service.setMuteAt(SonarChannel.Chat, SonarMix.monitoring, true, 1_000);
        service.flushDue(1_100);

        assertTrue(service.level(SonarChannel.Chat, SonarMix.monitoring).orElseThrow().muted());
        assertEquals(0.6, service.level(SonarChannel.Chat, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
        assertEquals(List.of("/volumeSettings/streamer/monitoring/chatRender/isMuted/true"), client.writes);
    }

    @Test
    void aWriteWaitsOutItsCoalescingWindow() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.30, 1_000);
        service.flushDue(1_049);
        assertEquals(List.of(), client.writes);

        service.flushDue(1_050);
        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.3000"), client.writes);
    }

    @Test
    void theScheduledPollAlsoLetsARecentLocalWriteWin() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(true);
        service.replaceState(streamWithGameLevel());
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);
        client.served = new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.10, false)));

        service.setVolume(SonarChannel.Game, SonarMix.monitoring, 0.90);
        service.poll();

        assertEquals(0.90, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void aSteadySweepIsThrottledNotDebounced() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        for (var t = 1_000; t <= 1_050; t += 10) {
            service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, t / 10_000.0, t);
        }
        // The sweep never pauses, yet the first write of it is due 50 ms in and carries the latest value.
        service.flushDue(1_050);

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.1050"), client.writes);
    }

    @Test
    void aValueQueuedDuringAFlushIsSentInsteadOfTheStaleOne() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.30, 1_000);
        service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, 0.30, 1_000);
        // While the first route is on the wire, the dial moves the other one.
        var movedLater = new ArrayList<SonarChannel>();
        client.duringFirstWrite = sent -> {
            var target = sent.channel() == SonarChannel.Game ? SonarChannel.Chat : SonarChannel.Game;
            movedLater.add(target);
            service.setVolumeAt(target, SonarMix.monitoring, 0.40, 1_060);
        };

        service.flushDue(1_060);
        var other = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, movedLater.get(0));
        // The other route goes out once, carrying the value that arrived mid-flush, never the stale 0.30.
        assertEquals(other.volumePath(0.40), client.writes.get(1), () -> "first flush: " + client.writes);
        assertEquals(2, client.writes.size(), () -> "first flush: " + client.writes);

        service.flushDue(1_200);
        assertEquals(2, client.writes.size(), () -> "nothing left to send: " + client.writes);
    }

    @Test
    void twoRoutesDueTogetherBothKeepFlowingWhileTheDialMoves() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));
        var personal = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Chat);
        var stream = SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Chat);
        service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, 0.10, 1_000);
        service.setVolumeAt(SonarChannel.Chat, SonarMix.streaming, 0.10, 1_000);
        // A Both control mid-sweep: every HTTP write overlaps the next dial tick for both mixes.
        var tick = new double[] { 0.10 };
        client.duringEachWrite = sent -> {
            tick[0] += 0.01;
            service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, tick[0], 1_000);
            service.setVolumeAt(SonarChannel.Chat, SonarMix.streaming, tick[0], 1_000);
        };

        for (var now = 1_050; now <= 1_500; now += 50) {
            service.flushDue(now);
        }

        var personalWrites = client.writes.stream().filter(w -> w.startsWith(personal.volumePath(0).replace("0.0000", ""))).count();
        var streamWrites = client.writes.stream().filter(w -> w.startsWith(stream.volumePath(0).replace("0.0000", ""))).count();
        assertEquals(10, personalWrites, () -> "writes: " + client.writes);
        assertEquals(10, streamWrites, () -> "writes: " + client.writes);
    }

    @Test
    void theCallerThreadNeverSends() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.10, 1_000);
        // The first value is already due by now; it still waits for the scheduler.
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.20, 1_100);

        assertEquals(List.of(), client.writes);
    }

    @Test
    void flushingTwiceAtTheSameInstantSendsOnce() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.30, 1_000);
        service.flushDue(1_100);
        service.flushDue(1_100);

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.3000"), client.writes);
    }

    @Test
    void anOverlappingFlushDoesNotResendWhatTheOtherAlreadySent() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.30, 1_000);
        service.setVolumeAt(SonarChannel.Chat, SonarMix.monitoring, 0.40, 1_000);
        // While the first flush is inside its first HTTP write, a second flush runs to completion.
        client.duringFirstWrite = route -> service.flushDue(1_100);

        service.flushDue(1_100);

        assertEquals(2, client.writes.size(), () -> "writes: " + client.writes);
        assertEquals(2, client.writes.stream().distinct().count(), () -> "writes: " + client.writes);
    }

    @Test
    void aPollInADifferentModeKeepsNoRouteFromTheOldMode() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(streamWithGameLevel());
        var classicGame = SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game);

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.90, 10_000);
        // The user switched GG to Classic just after the dial moved.
        service.applyPolledAt(new SonarState(SonarMode.classic, Map.of(classicGame, new SonarLevel(0.25, false))), 10_500);

        assertEquals(Map.of(classicGame, new SonarLevel(0.25, false)), service.snapshot().levels());
        assertEquals(0.25, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void aWriteQueuedForTheOldModeIsNotSentAfterTheModeChanges() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.90, 10_000);
        service.applyPolledAt(new SonarState(SonarMode.classic, Map.of()), 10_010);
        service.flushDue(10_100);

        assertEquals(List.of(), client.writes);
    }

    @Test
    void writesBeforeTheModeIsKnownAreIgnored() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.50, 1_000);
        service.setMuteAt(SonarChannel.Game, SonarMix.monitoring, true, 1_000);
        service.flushDue(1_100);

        assertEquals(List.of(), client.writes);
        assertEquals(SonarState.UNKNOWN, service.snapshot());
    }

    @Test
    void losingTheModeDropsQueuedWrites() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.50, 1_000);
        client.mode = null;
        service.poll();
        service.flushDue(1_100);

        assertEquals(List.of(), client.writes);
    }

    @Test
    void aWriteThatChangesNothingDoesNotFire() {
        var client = new RecordingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        service.replaceState(streamWithGameLevel());

        service.setMuteAt(SonarChannel.Game, SonarMix.monitoring, true, 1_000);
        service.setMuteAt(SonarChannel.Game, SonarMix.monitoring, true, 1_010);

        assertEquals(1, changed.fired.get());
    }

    @Test
    void aVolumeOnlyWriteDoesNotFire() {
        var client = new RecordingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);
        service.replaceState(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.5, false))));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.6, 1_000);
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.7, 1_010);

        assertEquals(0.7, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
        assertEquals(0, changed.fired.get(), "a dial sweep must not recompute every mute colour on each tick");
    }

    @Test
    void aMuteWriteFires() {
        var client = new RecordingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);
        service.replaceState(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.5, false))));

        service.setMuteAt(SonarChannel.Game, SonarMix.monitoring, true, 1_000);

        assertEquals(1, changed.fired.get());
    }

    @Test
    void aWriteBeforeTheLevelsAreReadIsSentButInventsNoState() {
        var client = new RecordingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        // Sonar is found, but nothing has read its levels yet.
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.40, 1_000);
        service.flushDue(1_100);

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.4000"), client.writes);
        assertNull(service.mutedOrNull(SonarChannel.Game, SonarMix.monitoring), "the mute state is still unknown");
        assertEquals(Map.of(), service.snapshot().levels());
        assertEquals(0, changed.fired.get());
    }

    @Test
    void theFirstPollAfterAWriteToAnUnreadRouteIsTakenAsIs() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of()));
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);

        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.40, 10_000);
        service.applyPolledAt(new SonarState(SonarMode.stream, Map.of(route, new SonarLevel(0.10, true))), 10_500);

        assertEquals(Boolean.TRUE, service.mutedOrNull(SonarChannel.Game, SonarMix.monitoring),
                "no invented level may shield the real mute state from the poll");
    }
}
