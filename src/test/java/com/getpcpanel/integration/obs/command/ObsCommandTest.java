package com.getpcpanel.integration.obs.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandMapperTestFactory;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.integration.obs.command.CommandObsAction.ObsActionType;
import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * The OBS command family: every subtype registered by {@link ObsCommandModule} survives a JSON
 * round-trip through the polymorphic mapper (fields intact, nice {@code _type} id, legacy FQCN ids
 * still loading), labels render from the configured fields, and each {@link ObsActionType} maps to
 * its OBS WebSocket request name.
 */
@DisplayName("OBS commands: JSON round-trip + pure logic")
class ObsCommandTest {
    private final ObjectMapper mapper = CommandMapperTestFactory.mapperFor(new ObsCommandModule());

    private Command roundTrip(Command command) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(command), Command.class);
    }

    @Test
    @DisplayName("obs.action round-trips with its action type")
    void actionRoundTrip() throws Exception {
        var json = mapper.writeValueAsString(new CommandObsAction(ObsActionType.TOGGLE_STREAM));
        assertTrue(json.contains("\"obs.action\""), () -> "expected the nice id in: " + json);

        var loaded = assertInstanceOf(CommandObsAction.class, mapper.readValue(json, Command.class));
        assertEquals(ObsActionType.TOGGLE_STREAM, loaded.getAction());
        assertEquals("OBS: Toggle streaming", loaded.buildLabel());
    }

    @Test
    @DisplayName("obs.mute-source round-trips with source + mute type")
    void muteSourceRoundTrip() throws Exception {
        var loaded = assertInstanceOf(CommandObsMuteSource.class, roundTrip(new CommandObsMuteSource("Mic/Aux", MuteType.toggle)));
        assertEquals("Mic/Aux", loaded.getSource());
        assertEquals(MuteType.toggle, loaded.getType());
        assertEquals("Mute source: Mic/Aux (toggle)", loaded.buildLabel());
    }

    @Test
    @DisplayName("obs.set-scene round-trips with the scene name")
    void setSceneRoundTrip() throws Exception {
        var loaded = assertInstanceOf(CommandObsSetScene.class, roundTrip(new CommandObsSetScene("Gaming")));
        assertEquals("Gaming", loaded.getScene());
        assertEquals("Set scene: Gaming", loaded.buildLabel());
    }

    @Test
    @DisplayName("obs.set-source-volume round-trips with source name + dial params")
    void setSourceVolumeRoundTrip() throws Exception {
        var loaded = assertInstanceOf(CommandObsSetSourceVolume.class,
                roundTrip(new CommandObsSetSourceVolume("Desktop Audio", new DialCommandParams(true, 10, 20))));
        assertEquals("Desktop Audio", loaded.getSourceName());
        assertEquals(new DialCommandParams(true, 10, 20), loaded.getDialParams());
        assertEquals("Source volume: Desktop Audio", loaded.buildLabel());
    }

    @Test
    @DisplayName("a legacy FQCN _type id from an old save still loads")
    void legacyIdLoads() throws Exception {
        var loaded = mapper.readValue("{\"_type\":\"com.getpcpanel.commands.command.CommandObsSetScene\",\"scene\":\"Intro\"}", Command.class);
        assertInstanceOf(CommandObsSetScene.class, loaded);
        assertEquals("Intro", ((CommandObsSetScene) loaded).getScene());
    }

    @Test
    @DisplayName("every ObsActionType carries its OBS WebSocket request name")
    void actionTypeRequestNames() {
        assertEquals("StartStream", ObsActionType.START_STREAM.getRequestType());
        assertEquals("StopRecord", ObsActionType.STOP_RECORD.getRequestType());
        assertEquals("ToggleRecordPause", ObsActionType.TOGGLE_RECORD_PAUSE.getRequestType());
        assertEquals("ToggleVirtualCam", ObsActionType.TOGGLE_VIRTUAL_CAM.getRequestType());
        assertEquals("SaveReplayBuffer", ObsActionType.SAVE_REPLAY_BUFFER.getRequestType());
    }

    @Test
    @DisplayName("a null action renders an empty-suffix label and executes as a no-op")
    void nullActionIsSafe() {
        var command = new CommandObsAction(null);
        assertEquals("OBS: ", command.buildLabel());
        // No OBS bean is registered in this test, so reaching for the container would throw.
        command.execute();
    }
}
