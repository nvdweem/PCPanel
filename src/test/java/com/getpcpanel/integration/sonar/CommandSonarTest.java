package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandMapperTestFactory;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.sonar.command.CommandSonarMute;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.sonar.command.SonarCommandModule;
import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * The Sonar command family: both subtypes registered by {@link SonarCommandModule} survive a JSON
 * round-trip through the polymorphic mapper under their stable {@code _type} id, exactly like every
 * other integration's command family test (see {@code ObsCommandTest}, {@code CommandOscSendTest}).
 */
@DisplayName("Sonar commands: JSON round-trip")
class CommandSonarTest {
    private final ObjectMapper mapper = CommandMapperTestFactory.mapperFor(new SonarCommandModule());

    @Test
    @DisplayName("sonar.volume round-trips with its stable type id, channel and mix")
    void volumeCommandSerialisesUnderItsStableTypeId() throws Exception {
        var json = mapper.writeValueAsString(new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null));

        assertTrue(json.contains("\"sonar.volume\""), json);
        assertTrue(json.contains("\"channel\":\"Game\""), json);
        assertTrue(json.contains("\"mix\":\"monitoring\""), json);
    }

    @Test
    @DisplayName("sonar.volume round-trips unMuteOnVolumeChange")
    void volumeCommandRoundTripsUnmuteOnVolumeChange() throws Exception {
        var json = mapper.writeValueAsString(new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, true, null));
        assertTrue(json.contains("\"unMuteOnVolumeChange\":true"), json);

        var restored = assertInstanceOf(CommandSonarVolume.class, mapper.readValue(json, Command.class));
        assertTrue(restored.isUnMuteOnVolumeChange());
    }

    @Test
    @DisplayName("a saved sonar.volume without unMuteOnVolumeChange loads with it off")
    void savedVolumeWithoutUnmuteLoadsOff() throws Exception {
        var loaded = assertInstanceOf(CommandSonarVolume.class, mapper.readValue(
                "{\"_type\":\"sonar.volume\",\"channel\":\"Game\",\"mix\":\"monitoring\"}", Command.class));

        assertFalse(loaded.isUnMuteOnVolumeChange());
    }

    @Test
    @DisplayName("sonar.mute round-trips with its channel, mix and mute type")
    void muteCommandRoundTripsItsMuteType() throws Exception {
        var original = new CommandSonarMute(SonarChannel.Chat, SonarMixSelection.streaming, MuteType.toggle);

        var restored = assertInstanceOf(CommandSonarMute.class, mapper.readValue(mapper.writeValueAsString(original), Command.class));

        assertEquals(SonarChannel.Chat, restored.getChannel());
        assertEquals(SonarMixSelection.streaming, restored.getMix());
        assertEquals(MuteType.toggle, restored.getMuteType());
    }

    @Test
    @DisplayName("both commands are registered with the module")
    void bothCommandsAreRegisteredWithTheModule() {
        var types = new SonarCommandModule().commandTypes();

        assertTrue(types.contains(CommandSonarVolume.class));
        assertTrue(types.contains(CommandSonarMute.class));
    }

    @Test
    @DisplayName("a missing channel or mix defaults rather than deserialising to null")
    void missingChannelOrMixDefaults() throws Exception {
        var loaded = assertInstanceOf(CommandSonarVolume.class,
                mapper.readValue("{\"_type\":\"sonar.volume\"}", Command.class));

        assertEquals(SonarChannel.Game, loaded.getChannel());
        assertEquals(SonarMixSelection.monitoring, loaded.getMix());
    }

    @Test
    @DisplayName("labels name the integration SteelSeries Sonar and the mixes as GG does")
    void labelsNameTheIntegrationSteelSeriesSonar() {
        assertEquals("Game (Personal Mix) — SteelSeries Sonar",
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null).buildLabel());
        assertEquals("(Un)Mute Chat (Stream Mix) — SteelSeries Sonar",
                new CommandSonarMute(SonarChannel.Chat, SonarMixSelection.streaming, MuteType.toggle).buildLabel());
    }

    @Test
    @DisplayName("a Both command is labelled for both mixes")
    void bothIsLabelledForBothMixes() {
        assertEquals("Game (Both mixes) — SteelSeries Sonar",
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.both, false, null).buildLabel());
        assertEquals("(Un)Mute Mic (Both mixes) — SteelSeries Sonar",
                new CommandSonarMute(SonarChannel.Mic, SonarMixSelection.both, MuteType.mute).buildLabel());
    }

    @Test
    @DisplayName("a volume command that unmutes says so in its label")
    void unmutingVolumeIsLabelled() {
        assertEquals("Game (Personal Mix) (unmute) — SteelSeries Sonar",
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, true, null).buildLabel());
    }

    @Test
    @DisplayName("a Both mix round-trips as \"both\"")
    void bothMixRoundTrips() throws Exception {
        var json = mapper.writeValueAsString(new CommandSonarVolume(SonarChannel.Aux, SonarMixSelection.both, false, null));
        assertTrue(json.contains("\"mix\":\"both\""), json);

        var restored = assertInstanceOf(CommandSonarVolume.class, mapper.readValue(json, Command.class));
        assertEquals(SonarMixSelection.both, restored.getMix());

        var mute = assertInstanceOf(CommandSonarMute.class, mapper.readValue(
                "{\"_type\":\"sonar.mute\",\"channel\":\"Chat\",\"mix\":\"both\",\"muteType\":\"toggle\"}", Command.class));
        assertEquals(SonarMixSelection.both, mute.getMix());
    }

    @Test
    @DisplayName("saved monitoring and streaming mixes still load")
    void savedSingleMixesStillLoad() throws Exception {
        var monitoring = assertInstanceOf(CommandSonarVolume.class, mapper.readValue(
                "{\"_type\":\"sonar.volume\",\"channel\":\"Game\",\"mix\":\"monitoring\"}", Command.class));
        var streaming = assertInstanceOf(CommandSonarMute.class, mapper.readValue(
                "{\"_type\":\"sonar.mute\",\"channel\":\"Game\",\"mix\":\"streaming\",\"muteType\":\"mute\"}", Command.class));

        assertEquals(SonarMixSelection.monitoring, monitoring.getMix());
        assertEquals(SonarMixSelection.streaming, streaming.getMix());
    }
}
