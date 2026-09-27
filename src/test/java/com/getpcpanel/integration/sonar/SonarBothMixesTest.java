package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.integration.sonar.command.CommandSonarMute;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.testutil.FakeCdi;
import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;
import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * {@link SonarMixSelection#both}: one command driving the Personal and the Stream Mix together. Runs the
 * real command → {@link SonarService} → flush path against a recording client, so the assertions are on
 * the HTTP writes Sonar would receive.
 */
@DisplayName("Sonar: the Both mix selection")
class SonarBothMixesTest {
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

    /** Game's level on each given mix; a null flag leaves that mix unread. */
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
    @DisplayName("a Both dial writes the Personal and the Stream Mix in Streamer mode")
    void bothVolumeWritesEachMixInStreamerMode() {
        var client = new RecordingClient();
        var service = serviceServing(client, new SonarState(SonarMode.stream, Map.of()));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, false, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/Volume/1.0000",
                             "/volumeSettings/streamer/streaming/game/Volume/1.0000"), flushed(service, client));
    }

    @Test
    @DisplayName("a Both dial sends exactly one write in Classic mode")
    void bothVolumeSendsOneWriteInClassicMode() {
        var client = new RecordingClient();
        var service = serviceServing(client, new SonarState(SonarMode.classic, Map.of()));

        new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, false, null).execute(dialAt(255));

        assertEquals(List.of("/volumeSettings/classic/game/Volume/1.0000"), flushed(service, client));
    }

    @Test
    @DisplayName("a Both mute button writes both mixes in Streamer mode and one route in Classic mode")
    void bothMuteWritesEachMixOnce() {
        var streamClient = new RecordingClient();
        var stream = serviceServing(streamClient, new SonarState(SonarMode.stream, Map.of()));
        new CommandSonarMute(SonarChannel.Chat, SonarMixSelection.both, MuteType.mute).execute();
        assertEquals(List.of("/volumeSettings/streamer/monitoring/chatRender/isMuted/true",
                             "/volumeSettings/streamer/streaming/chatRender/isMuted/true"), flushed(stream, streamClient));

        var classicClient = new RecordingClient();
        var classic = serviceServing(classicClient, new SonarState(SonarMode.classic, Map.of()));
        new CommandSonarMute(SonarChannel.Chat, SonarMixSelection.both, MuteType.mute).execute();
        assertEquals(List.of("/volumeSettings/classic/chatRender/Mute/true"), flushed(classic, classicClient));
    }

    @ParameterizedTest(name = "Personal muted {0}, Stream muted {1} → {2}")
    @CsvSource({ "true, true, true", "true, false, false", "false, true, false", "false, false, false" })
    @DisplayName("Both reads as muted only when both mixes are muted")
    void bothIsMutedOnlyWhenBothMixesAre(boolean monitoringMuted, boolean streamingMuted, boolean expected) {
        var service = serviceServing(new RecordingClient(), streamGame(monitoringMuted, streamingMuted));
        var resolver = new SonarMuteResolver(service);
        var control = new Commands(List.of(new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, false, null)), null);

        assertEquals(expected, service.mutedOrNull(SonarChannel.Game, SonarMixSelection.both));
        assertEquals(Optional.of(expected), resolver.resolve(control, MuteStateResolver.FOLLOW));
    }

    @Test
    @DisplayName("Both's mute state is unknown while either mix's level is unread")
    void bothIsUnknownWhenEitherLevelIsMissing() {
        var onlyPersonal = serviceServing(new RecordingClient(), streamGame(true, null));
        assertNull(onlyPersonal.mutedOrNull(SonarChannel.Game, SonarMixSelection.both));

        var onlyStream = serviceServing(new RecordingClient(), streamGame(null, true));
        assertNull(onlyStream.mutedOrNull(SonarChannel.Game, SonarMixSelection.both));

        var onlyStreamUnmuted = serviceServing(new RecordingClient(), streamGame(null, false));
        assertNull(onlyStreamUnmuted.mutedOrNull(SonarChannel.Game, SonarMixSelection.both));
    }

    @Test
    @DisplayName("toggling Both unmutes both mixes when both are muted")
    void bothToggleUnmutesWhenBothAreMuted() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, true));

        new CommandSonarMute(SonarChannel.Game, SonarMixSelection.both, MuteType.toggle).execute();

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/isMuted/false",
                             "/volumeSettings/streamer/streaming/game/isMuted/false"), flushed(service, client));
    }

    @ParameterizedTest(name = "Personal muted {0}, Stream muted {1}")
    @CsvSource({ "true, false", "false, true", "false, false" })
    @DisplayName("toggling Both mutes both mixes unless both are muted")
    void bothToggleMutesUnlessBothAreMuted(boolean monitoringMuted, boolean streamingMuted) {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(monitoringMuted, streamingMuted));

        new CommandSonarMute(SonarChannel.Game, SonarMixSelection.both, MuteType.toggle).execute();

        assertEquals(List.of("/volumeSettings/streamer/monitoring/game/isMuted/true",
                             "/volumeSettings/streamer/streaming/game/isMuted/true"), flushed(service, client));
    }

    @Test
    @DisplayName("toggling Both with either mix unread sends nothing")
    void bothToggleWithAnUnknownMixSendsNothing() {
        var client = new RecordingClient();
        var service = serviceServing(client, streamGame(true, null));

        new CommandSonarMute(SonarChannel.Game, SonarMixSelection.both, MuteType.toggle).execute();

        assertEquals(List.of(), flushed(service, client));
    }
}
