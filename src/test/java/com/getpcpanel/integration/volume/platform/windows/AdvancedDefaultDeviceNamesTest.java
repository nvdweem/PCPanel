package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.DataFlow;

class AdvancedDefaultDeviceNamesTest {
    private static final String TYPE = "volume.default-device-advanced";
    private static final String HEADSET_ID = "{0.0.0.00000000}.{aaaaaaaa-0000-0000-0000-000000000000}";
    private static final String TWIN_A = "{0.0.0.00000000}.{11111111-0000-0000-0000-000000000000}";
    private static final String TWIN_B = "{0.0.0.00000000}.{22222222-0000-0000-0000-000000000000}";
    private static final String YETI_ID = "{0.0.1.00000000}.{bbbbbbbb-0000-0000-0000-000000000000}";
    private static final String HEADSET_MIC_ID = "{0.0.1.00000000}.{cccccccc-0000-0000-0000-000000000000}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<AudioDevice> present = new ArrayList<>(List.of(
            device("Headset (USB Audio)", HEADSET_ID, DataFlow.dfRender),
            device("Monitor (NVIDIA High Definition Audio)", TWIN_A, DataFlow.dfRender),
            device("Monitor (NVIDIA High Definition Audio)", TWIN_B, DataFlow.dfRender),
            device("Microphone (Yeti Stereo Microphone)", YETI_ID, DataFlow.dfCapture),
            device("Headset Microphone (USB Audio)", HEADSET_MIC_ID, DataFlow.dfCapture)));

    @Test
    void aNameOfOneDeviceBecomesItsIdAndStaysAsTheName() {
        var command = command("mediaPb", "headset (usb audio)");

        assertTrue(pin(command));

        assertEquals(HEADSET_ID, command.path("mediaPb").asText());
        assertEquals("headset (usb audio)", command.path("mediaPbName").asText());
    }

    @Test
    void partOfTheNameOfOneDeviceIsPinnedToo() {
        var command = command("mediaRec", "Yeti");

        assertTrue(pin(command));

        assertEquals(YETI_ID, command.path("mediaRec").asText());
        assertEquals("Yeti", command.path("mediaRecName").asText());
    }

    @Test
    void aNameTwoDevicesShareIsLeftAlone() {
        var command = command("mediaPb", "Monitor (NVIDIA High Definition Audio)");

        assertFalse(pin(command));

        assertEquals("Monitor (NVIDIA High Definition Audio)", command.path("mediaPb").asText());
        assertTrue(command.path("mediaPbName").isMissingNode());
    }

    @Test
    void partOfTheNameOfSeveralDevicesIsLeftAlone() {
        var command = command("mediaPb", "Audio");

        assertFalse(pin(command));

        assertEquals("Audio", command.path("mediaPb").asText());
    }

    @Test
    void anExactNameIsPreferredOverLongerNamesContainingIt() {
        present.add(device("Headset", "{0.0.0.00000000}.{dddddddd-0000-0000-0000-000000000000}", DataFlow.dfRender));
        present.add(device("Headset Earphone (3- Wireless)", "{0.0.0.00000000}.{eeeeeeee-0000-0000-0000-000000000000}", DataFlow.dfRender));
        var command = command("mediaPb", "Headset");

        assertTrue(pin(command));

        assertEquals("{0.0.0.00000000}.{dddddddd-0000-0000-0000-000000000000}", command.path("mediaPb").asText());
    }

    @Test
    void onlyDevicesOnTheFieldsFlowCount() {
        // "USB Audio" names one playback and one recording device: unique on each flow.
        var command = command("communicationPb", "USB Audio");
        command.put("communicationRec", "USB Audio");

        assertTrue(pin(command));

        assertEquals(HEADSET_ID, command.path("communicationPb").asText());
        assertEquals(HEADSET_MIC_ID, command.path("communicationRec").asText());
    }

    @Test
    void aFieldThatHasANameIsNeverRewritten() {
        var command = command("mediaPb", "{0.0.0.00000000}.{99999999-0000-0000-0000-000000000000}");
        command.put("mediaPbName", "Headset (USB Audio)");

        assertFalse(pin(command));

        assertEquals("{0.0.0.00000000}.{99999999-0000-0000-0000-000000000000}", command.path("mediaPb").asText());
    }

    @Test
    void anIdWithoutANameGetsItsConnectedDevicesName() {
        var command = command("mediaPb", TWIN_B);

        assertTrue(pin(command));

        assertEquals(TWIN_B, command.path("mediaPb").asText());
        assertEquals("Monitor (NVIDIA High Definition Audio)", command.path("mediaPbName").asText());
    }

    @Test
    void anIdThatIsNotConnectedIsLeftAlone() {
        var command = command("mediaPb", "{0.0.0.00000000}.{99999999-0000-0000-0000-000000000000}");

        assertFalse(pin(command));

        assertTrue(command.path("mediaPbName").isMissingNode());
    }

    @Test
    void aNameOfNoDeviceAndBlankFieldsAreLeftAlone() {
        var command = command("mediaPb", "Speakers");
        command.put("mediaRec", "");
        command.putNull("communicationPb");

        assertFalse(pin(command));
    }

    @Test
    void otherActionsWithTheSameFieldsAreLeftAlone() {
        var command = MAPPER.createObjectNode().put(Command.TYPE_PROPERTY, "volume.default-device-toggle-advanced").put("mediaPb", "Headset (USB Audio)");

        assertFalse(pin(command));
    }

    @Test
    void reachesActionsAnywhereInTheSaveUnderTheirLegacyTypeToo() {
        var save = MAPPER.createObjectNode();
        var nested = save.putObject("devices").putArray("buttons").addObject().putArray("commands");
        nested.addObject().put(Command.TYPE_PROPERTY, "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced").put("mediaRec", "Yeti");

        assertTrue(AdvancedDefaultDeviceNames.pin(save, present));

        assertEquals(YETI_ID, nested.get(0).path("mediaRec").asText());
    }

    @Test
    void writesTheFieldsTheActionReads() {
        var mapper = AppLikeMapper.build();
        var command = CommandVolumeDefaultDeviceAdvanced.builder().name("Desk").mediaPb("Headset (USB Audio)").mediaRec("Yeti").build();
        JsonNode tree = mapper.valueToTree(command);

        assertTrue(AdvancedDefaultDeviceNames.pin(tree, present));

        var read = (CommandVolumeDefaultDeviceAdvanced) mapper.convertValue(tree, Command.class);
        assertEquals(HEADSET_ID, read.getMediaPb());
        assertEquals("Headset (USB Audio)", read.getMediaPbName());
        assertEquals(YETI_ID, read.getMediaRec());
        assertEquals("Yeti", read.getMediaRecName());
    }

    private boolean pin(ObjectNode command) {
        return AdvancedDefaultDeviceNames.pin(command, present);
    }

    private static ObjectNode command(String field, String value) {
        return MAPPER.createObjectNode().put(Command.TYPE_PROPERTY, TYPE).put(field, value);
    }

    private static AudioDevice device(String name, String id, DataFlow flow) {
        return new WindowsAudioDevice(null, null, name, id, null).dataflow(flow);
    }
}
