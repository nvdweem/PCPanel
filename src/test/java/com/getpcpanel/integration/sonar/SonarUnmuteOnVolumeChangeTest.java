package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.testutil.FakeCdi;

/**
 * {@link CommandSonarVolume}'s "Unmute on volume change": a dial move unmutes each selected mix that is
 * known to be muted, and leaves an unmuted or unread mix alone. Runs the real command → {@link SonarService}
 * → flush path against a recording client, so the assertions are on the HTTP writes Sonar would receive.
 */
@DisplayName("Sonar: unmute on volume change")
class SonarUnmuteOnVolumeChangeTest {
    private static final class RecordingClient extends SonarClient {
        final List<String> writes = new ArrayList<>();

        RecordingClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public Optional<SonarMode> fetchMode() {
            return Optional.empty();
        }

        @Override public boolean setVolume(SonarRoute route, double value) {
            writes.add(route.volumePath(value));
            return true;
        }

        @Override public boolean setMute(SonarRoute route, boolean muted) {
            writes.add(route.mutePath(muted));
            return true;
        }
    }

    @AfterEach
    void tearDown() {
        FakeCdi.clear();
    }

    private static SonarService serviceServing(RecordingClient client, SonarState state) {
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(state);
        FakeCdi.register(SonarService.class, service);
        return service;
    }

    /** Game's level on each given mix in Streamer mode; a null flag leaves that mix unread. */
    private static SonarState streamGame(Boolean monitoringMuted, Boolean streamingMuted) {
        var levels = new HashMap<SonarRoute, SonarLevel>();
        if (monitoringMuted != null) {
            levels.put(SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game), new SonarLevel(0.5, monitoringMuted));
        }
        if (streamingMuted != null) {
            levels.put(SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Game), new SonarLevel(0.5, streamingMuted));
        }
        return new SonarState(SonarMode.stream, Map.copyOf(levels));
    }

    private static DialActionParameters dialAt(int raw) {
        return new DialActionParameters("device", false, new DialValue(null, Curve.LINEAR, raw));
    }

    private static List<String> flushed(SonarService service, RecordingClient client) {
        service.flushDue(Long.MAX_VALUE);
        return client.writes.stream().sorted().toList();
    }

    @Test
    @DisplayName("with the option off a muted channel stays muted")
    void optionOffLeavesMutedChannelMuted() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, null));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000"), flushed(service, client));
        assertEquals(Boolean.TRUE, service.mutedOrNull(SonarChannel.Game, SonarMix.monitoring));
    }

    @Test
    @DisplayName("with the option on a muted channel is unmuted and its volume still written")
    void optionOnUnmutesAMutedChannel() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, null));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000",
                             "/volumeSettings/streamer/monitoring/game/isMuted/false"), flushed(service, client));
    }

    @Test
    @DisplayName("with the option on an unmuted channel gets no mute write")
    void optionOnLeavesAnUnmutedChannelAlone() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(false, null));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000"), flushed(service, client));
    }

    @Test
    @DisplayName("with the option on a channel whose mute state is unread gets no mute write")
    void optionOnLeavesAnUnknownChannelAlone() {
        var client = new RecordingClient();
        var service = serviceServing(client, new SonarState(SonarMode.stream, Map.of()));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000"), flushed(service, client));
    }

    @Test
    @DisplayName("with Both, only the mix that is muted is unmuted")
    void bothUnmutesOnlyTheMutedMix() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(false, true));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, true, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000",
                             "/volumeSettings/streamer/streaming/game/Volume/1.0000",
                             "/volumeSettings/streamer/streaming/game/isMuted/false"), flushed(service, client));
    }

    @Test
    @DisplayName("with Both in Classic mode a muted channel gets one unmute")
    void bothInClassicModeUnmutesOnce() {
        var client = new RecordingClient();
        var classicGame = new SonarState(SonarMode.classic,
                Map.of(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game), new SonarLevel(0.5, true)));
        var service = serviceServing(client, classicGame);
        var command = new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, true, null);

        command.execute(dialAt(255));
        service.flushDue(Long.MAX_VALUE);

        assertEquals(List.of("/volumeSettings/classic/game/Mute/false", "/volumeSettings/classic/game/Volume/1.0000"),
                client.writes.stream().sorted().toList());
    }

    @Test
    @DisplayName("a second tick of the sweep sends no further unmute")
    void secondTickSendsNoFurtherUnmute() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, null));
        var command = new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null);

        command.execute(dialAt(255));
        service.flushDue(Long.MAX_VALUE);
        client.writes.clear();
        command.execute(dialAt(0));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/0.0000"), flushed(service, client));
    }

    @Test
    @DisplayName("the position the device reports when it connects does not unmute")
    void initialValueDoesNotUnmute() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, null));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null)
                .execute(new DialActionParameters("device", true, new DialValue(null, Curve.LINEAR, 255)));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000"), flushed(service, client));
    }
}
