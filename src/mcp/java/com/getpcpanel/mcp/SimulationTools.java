package com.getpcpanel.mcp;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.provider.deej.DeejProtocol;
import com.getpcpanel.device.provider.midi.MidiProtocol;
import com.getpcpanel.hid.DeviceCommunicationHandler.ButtonPressEvent;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner.DeviceConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.wavelink.WaveLinkService;

import dev.niels.wavelink.impl.model.WaveLinkChannel;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import lombok.extern.log4j.Log4j2;

/**
 * The hardware-free test harness: inject synthetic device input onto the same CDI event bus the real
 * input layer uses, and create/destroy virtual devices with no hardware. Effects are observed via
 * {@code pcpanel_get_audio_state} / {@code pcpanel_get_device}.
 *
 * <p>Events are fired the same way the real input path fires them and are processed asynchronously in
 * places, so every tool returns promptly with an ack - the agent then polls for the effect rather
 * than expecting a synchronous result.
 */
@Log4j2
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class SimulationTools {
    @Inject Event<Object> eventBus;
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;
    @Inject ObjectMapper objectMapper;
    @Inject WaveLinkService waveLink;

    @Tool(description = "Drive an analog control (knob/slider) as if the hardware moved it: fires the "
            + "same KnobRotateEvent the input layer fires. value is the canonical 0-255 domain "
            + "(clamped). Then poll pcpanel_get_audio_state / pcpanel_get_device for the effect.")
    public Ack pcpanel_simulate_analog(
            @ToolArg(description = "Device serial / id") String serial,
            @ToolArg(description = "Analog (knob/slider) index") int index,
            @ToolArg(description = "Value in the canonical 0-255 domain") int value0to255) {
        if (deviceHolder.getDevice(serial).isEmpty()) {
            return new Ack(false, "No connected device with serial '" + serial + "'");
        }
        var clamped = Math.max(0, Math.min(255, value0to255));
        eventBus.fire(new KnobRotateEvent(serial, index, clamped, false));
        return new Ack(true, "Fired KnobRotateEvent(" + serial + ", knob=" + index + ", value=" + clamped + ")");
    }

    @Tool(description = "Press or release a button as if the hardware did: fires the same "
            + "ButtonPressEvent the input layer fires (a press also triggers the configured click "
            + "action).")
    public Ack pcpanel_simulate_button(
            @ToolArg(description = "Device serial / id") String serial,
            @ToolArg(description = "Button index") int index,
            @ToolArg(description = "true = pressed, false = released") boolean pressed) {
        if (deviceHolder.getDevice(serial).isEmpty()) {
            return new Ack(false, "No connected device with serial '" + serial + "'");
        }
        eventBus.fire(new ButtonPressEvent(serial, index, pressed));
        return new Ack(true, "Fired ButtonPressEvent(" + serial + ", button=" + index + ", pressed=" + pressed + ")");
    }

    @Tool(description = "Feed a raw Deej serial line (e.g. \"0|512|1023\") through the real "
            + "DeejProtocol parse + normalize, then fire a KnobRotateEvent per slider (value 0-1023 -> "
            + "0-255). Note: per-port dead-band conditioning is provider state and is not applied here.")
    public DeejResult pcpanel_simulate_deej_line(
            @ToolArg(description = "Device serial / id") String serial,
            @ToolArg(description = "Raw Deej line, pipe-separated 10-bit values, e.g. 0|512|1023") String line) {
        if (deviceHolder.getDevice(serial).isEmpty()) {
            return new DeejResult(false, "No connected device with serial '" + serial + "'", List.of());
        }
        var raw = DeejProtocol.parse(line);
        if (raw == null) {
            return new DeejResult(false, "Unparseable Deej line: '" + line + "'", List.of());
        }
        var fired = new ArrayList<DeejChannel>();
        for (var i = 0; i < raw.length; i++) {
            var norm = DeejProtocol.normalize(raw[i]);
            eventBus.fire(new KnobRotateEvent(serial, i, norm, false));
            fired.add(new DeejChannel(i, raw[i], norm));
        }
        return new DeejResult(true, null, fired);
    }

    @Tool(description = "Feed a raw MIDI message (status, data1, data2) through the real MidiProtocol "
            + "decode + normalize, then fire a KnobRotateEvent (CONTROL_CHANGE) or ButtonPressEvent "
            + "(NOTE on/off). The control index used is the MIDI CC/note number; mapping that to a "
            + "learned flat index requires a live MidiProvider device, so bind config by CC/note.")
    public MidiResult pcpanel_simulate_midi(
            @ToolArg(description = "Device serial / id") String serial,
            @ToolArg(description = "MIDI status byte (e.g. 0xB0 CC, 0x90 note-on)") int status,
            @ToolArg(description = "data1 (CC number / note)") int data1,
            @ToolArg(description = "data2 (value / velocity)") int data2) {
        if (deviceHolder.getDevice(serial).isEmpty()) {
            return new MidiResult(false, "No connected device with serial '" + serial + "'", null, 0);
        }
        var event = MidiProtocol.decode(status, data1, data2);
        if (event == null) {
            return new MidiResult(false, "Undecodable / ignored MIDI message", null, 0);
        }
        if (event.kind() == MidiProtocol.MidiKind.ANALOG) {
            var norm = MidiProtocol.normalize7bit(event.value());
            eventBus.fire(new KnobRotateEvent(serial, event.number(), norm, false));
            return new MidiResult(true, null, "ANALOG", norm);
        }
        eventBus.fire(new ButtonPressEvent(serial, event.number(), event.pressed()));
        return new MidiResult(true, null, "BUTTON", event.pressed() ? 1 : 0);
    }

    @Tool(description = "Register a synthetic device from a DeviceDescriptor JSON with NO hardware - "
            + "fires the real DeviceConnectedEvent so it appears in pcpanel_list_devices, renders in "
            + "the UI, and accepts simulated input. Use a non-'pcpanel' providerId and null "
            + "globalLighting (e.g. a fake Deej) so no HID output is attempted. Remove it with "
            + "pcpanel_remove_virtual_device.")
    public Ack pcpanel_create_virtual_device(
            @ToolArg(description = "Device serial / id to register it under") String serial,
            @ToolArg(description = "DeviceDescriptor as JSON (providerId, deviceKindId, displayName, "
                    + "analogInputs[], digitalInputs[], lightOutputs[], analogOutputs[], globalLighting)") String descriptorJson) {
        DeviceDescriptor descriptor;
        try {
            descriptor = objectMapper.readValue(descriptorJson, DeviceDescriptor.class);
        } catch (Exception e) {
            return new Ack(false, "Invalid descriptor JSON: " + e.getMessage());
        }
        if ("pcpanel".equals(descriptor.providerId()) && descriptor.globalLighting() != null) {
            return new Ack(false, "A virtual device must not be a lighting-capable 'pcpanel' device: it "
                    + "would attempt HID output. Use a non-pcpanel providerId and null globalLighting.");
        }
        if (deviceHolder.getDevice(serial).isPresent()) {
            return new Ack(false, "A device with serial '" + serial + "' is already connected");
        }
        // deviceType is null for non-PCPanel; DeviceHolder.deviceAdded builds a descriptor-only
        // GenericDevice and skips the HID init/lighting because globalLighting is null.
        eventBus.fire(new DeviceConnectedEvent(serial, null, descriptor));
        return new Ack(true, "Registered virtual device '" + serial + "' (" + descriptor.providerId() + "/"
                + descriptor.deviceKindId() + ")");
    }

    @Tool(description = "Remove a virtual (or any) device: fires the DeviceDisconnectedEvent so it "
            + "leaves the live device list, and purges its persisted entry so it does not linger as an "
            + "offline device.")
    public Ack pcpanel_remove_virtual_device(@ToolArg(description = "Device serial / id") String serial) {
        var present = deviceHolder.getDevice(serial).isPresent();
        eventBus.fire(new DeviceDisconnectedEvent(serial));
        var purged = saveService.get().getDevices().remove(serial) != null;
        if (purged) {
            saveService.save();
        }
        if (!present && !purged) {
            return new Ack(false, "No device (live or persisted) with serial '" + serial + "'");
        }
        return new Ack(true, "Removed device '" + serial + "'" + (purged ? " and purged its saved config" : ""));
    }

    @Tool(description = "Simulate a Wave Link channel state update (incl. mute) through the REAL listener "
            + "path - injects the channel into the live Wave Link model and fires channelChanged, exactly "
            + "as an incoming Wave Link push would. Drives the mute-override colour for any control bound "
            + "to this channel's volume. channelId must match the id the control's command targets. Then "
            + "poll pcpanel_get_device for the control's colour, or pcpanel_recent_logs for the decision.")
    public Ack pcpanel_simulate_wavelink_mute(
            @ToolArg(description = "Wave Link channel id (must match the bound command's id1)") String channelId,
            @ToolArg(description = "Channel display name (cosmetic, e.g. 'Music')") String name,
            @ToolArg(description = "true = muted, false = unmuted") boolean muted) {
        waveLink.simulateChannelState(new WaveLinkChannel(channelId, name, null, null, null, muted, null, null, null));
        return new Ack(true, "Injected Wave Link channel '" + channelId + "' (" + name + ") muted=" + muted);
    }

    public record Ack(boolean ok, String message) {
    }

    public record DeejResult(boolean ok, String error, List<DeejChannel> channels) {
    }

    public record DeejChannel(int index, int raw, int normalized) {
    }

    public record MidiResult(boolean ok, String error, String kind, int value) {
    }
}
