package dev.niels.wavelink.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.niels.wavelink.impl.model.WaveLinkGain;
import dev.niels.wavelink.impl.model.WaveLinkInput;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.rpc.JsonRpcMessage;
import dev.niels.wavelink.impl.rpc.WaveLinkInputDeviceChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetInputDeviceCommand;

/**
 * Guards the Wave Link input-device protocol against the wire format captured live from Wave Link 3.1.1:
 * {@code setInputDevice} sends the bare device ({@code {id, inputs:[{id, gain:{value}, isMuted}]}}), and
 * {@code inputDeviceChanged} pushes are partial (gain or mute, not both) and must merge with the cache.
 */
class WaveLinkInputProtocolTest {
    private static final String DEV = "{0.0.1.00000000}.{yeti}";
    private final ObjectMapper mapper = new ObjectMapper();

    /** A client that captures outgoing commands instead of sending them over a (non-existent) socket. */
    private static final class CapturingClient extends WaveLinkClientImpl {
        final List<WaveLinkJsonRpcCommand<?, ?>> sent = new ArrayList<>();

        CapturingClient() {
            super(false);
        }

        @Override
        protected <R> CompletableFuture<R> send(WaveLinkJsonRpcCommand<?, R> message) {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static WaveLinkInputDevice cachedDevice(double gain, boolean muted) {
        return new WaveLinkInputDevice(DEV, "Microphone (Yeti)", "thirdParty",
                List.of(new WaveLinkInput(DEV, "Microphone (Yeti)", new WaveLinkGain(gain, 0.0, 1.0, List.of()), muted)));
    }

    private JsonNode firstSentParams(CapturingClient client) throws Exception {
        var node = mapper.readTree(mapper.writeValueAsString(client.sent.get(0)));
        assertEquals("setInputDevice", node.get("method").asText());
        return node.get("params");
    }

    @Test
    void setInputSendsBareDeviceWithGainAndMute() throws Exception {
        var client = new CapturingClient();
        client.setInput(cachedDevice(1.0, false), 0.3, true);

        assertInstanceOf(WaveLinkSetInputDeviceCommand.class, client.sent.get(0));
        var params = firstSentParams(client);
        // Bare device (not wrapped in {inputDevice:...}); blank() drops name/deviceType so we never echo them back.
        assertEquals(DEV, params.get("id").asText());
        assertFalse(params.has("name"));
        assertFalse(params.has("deviceType"));
        var input = params.get("inputs").get(0);
        assertEquals(DEV, input.get("id").asText());
        assertEquals(0.3, input.get("gain").get("value").asDouble());
        assertFalse(input.get("gain").has("min"), "of() must omit server-owned min/max/lookUpTable");
        assertTrue(input.get("isMuted").asBoolean());
    }

    @Test
    void setInputLevelOnlyOmitsMute() throws Exception {
        var client = new CapturingClient();
        client.setInput(cachedDevice(1.0, false), 0.5, null);

        var input = firstSentParams(client).get("inputs").get(0);
        assertEquals(0.5, input.get("gain").get("value").asDouble());
        assertFalse(input.has("isMuted"), "a level-only set must not carry mute");
    }

    @Test
    void setInputMuteOnlyOmitsGain() throws Exception {
        var client = new CapturingClient();
        client.setInput(cachedDevice(1.0, false), null, true);

        var input = firstSentParams(client).get("inputs").get(0);
        assertTrue(input.get("isMuted").asBoolean());
        assertFalse(input.has("gain"), "a mute-only set must not carry gain");
    }

    @Test
    void parsesPolymorphicInputMessages() throws Exception {
        var parser = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        var changed = parser.readValue(
                "{\"jsonrpc\":\"2.0\",\"method\":\"inputDeviceChanged\",\"params\":{\"id\":\"" + DEV
                        + "\",\"name\":\"Microphone (Yeti)\",\"deviceType\":\"thirdParty\",\"inputs\":[{\"id\":\"" + DEV
                        + "\",\"gain\":{\"value\":0.3,\"min\":0,\"max\":1,\"lookUpTable\":[]}}]}}",
                JsonRpcMessage.class);
        var push = assertInstanceOf(WaveLinkInputDeviceChangedCommand.class, changed);
        assertEquals(0.3, push.getParams().inputs().get(0).gain().value());

        var set = parser.readValue(
                "{\"jsonrpc\":\"2.0\",\"method\":\"setInputDevice\",\"id\":2,\"params\":{\"id\":\"" + DEV
                        + "\",\"inputs\":[{\"id\":\"" + DEV + "\",\"gain\":{\"value\":1},\"isMuted\":false}]}}",
                JsonRpcMessage.class);
        assertInstanceOf(WaveLinkSetInputDeviceCommand.class, set);
    }

    @Test
    void inputDeviceChangedMergesPartialPushes() {
        var client = new CapturingClient();
        client.updateInputDevices(List.of(cachedDevice(1.0, false)));

        // Gain-only push (no isMuted) must keep the cached mute=false.
        client.onCommand(changedPush(new WaveLinkInput(DEV, "Microphone (Yeti)", WaveLinkGain.of(0.3), null)));
        var afterGain = client.getInputDevices().get(DEV).inputs().get(0);
        assertEquals(0.3, afterGain.gain().value());
        assertEquals(false, afterGain.isMuted());

        // Mute-only push (no gain) must keep the gain from the previous merge.
        client.onCommand(changedPush(new WaveLinkInput(DEV, null, null, true)));
        var afterMute = client.getInputDevices().get(DEV).inputs().get(0);
        assertEquals(0.3, afterMute.gain().value(), "mute push must not wipe cached gain");
        assertEquals(true, afterMute.isMuted());
    }

    private static WaveLinkInputDeviceChangedCommand changedPush(WaveLinkInput input) {
        var cmd = new WaveLinkInputDeviceChangedCommand();
        cmd.setParams(new WaveLinkInputDevice(DEV, null, null, List.of(input)));
        return cmd;
    }
}
