package com.getpcpanel.mcp;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.DeviceResource;
import com.getpcpanel.rest.MidiResource;
import com.getpcpanel.rest.ProVisualColorsService;
import com.getpcpanel.rest.SerialResource;
import com.getpcpanel.rest.model.dto.DeviceDto;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;
import com.getpcpanel.rest.model.dto.MidiDeviceDto;
import com.getpcpanel.rest.model.dto.SerialPortDto;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import lombok.extern.log4j.Log4j2;

/**
 * Read-only device, serial-port and MIDI introspection. Wraps the existing REST resources so an
 * agent gets the same data the Angular UI sees, but surfaces transport failures (a native
 * {@code UnsatisfiedLinkError}, an empty MIDI subsystem) as <em>data</em> rather than an opaque HTTP
 * 500.
 */
@Log4j2
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class DeviceIntrospectionTools {
    @Inject DeviceResource deviceResource;
    @Inject SerialResource serialResource;
    @Inject MidiResource midiResource;
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;
    @Inject ProVisualColorsService proVisualColorsService;

    @Tool(description = "List all devices, both live (connected=true) and persisted-but-offline "
            + "(connected=false). Each carries its capability descriptor (providerId, deviceKindId, "
            + "analog/digital/light specs).")
    public List<DeviceDto> pcpanel_list_devices() {
        return deviceResource.listDevices();
    }

    @Tool(description = "Full snapshot of one connected device by serial: descriptor, current profile "
            + "+ its assignments, live analog values, lighting config and resolved visual colors. "
            + "Returns found=false if the serial is not currently connected.")
    public DeviceSnapshot pcpanel_get_device(@ToolArg(description = "Device serial / id") String serial) {
        var device = deviceHolder.getDevice(serial).orElse(null);
        if (device == null) {
            return new DeviceSnapshot(false, "No connected device with serial '" + serial + "'", null);
        }
        var deviceSave = saveService.get().getDeviceSave(serial);
        var snapshot = DeviceSnapshotDto.from(device, deviceSave, proVisualColorsService);
        return new DeviceSnapshot(true, null, snapshot);
    }

    @Tool(description = "List available serial ports (for Deej devices). Returns available=false with "
            + "an error string instead of throwing when the serial backend is missing (e.g. a native "
            + "UnsatisfiedLinkError).")
    public SerialPorts pcpanel_list_serial_ports() {
        try {
            var ports = serialResource.listPorts();
            return new SerialPorts(true, null, ports);
        } catch (Throwable t) {
            log.debug("Serial port enumeration failed", t);
            return new SerialPorts(false, describe(t), List.of());
        }
    }

    @Tool(description = "List detected MIDI input devices. midiSubsystemAvailable is false (with a "
            + "note) when javax.sound.midi enumerates nothing - the known 'empty in the native image' "
            + "limitation - so it is explicit, not a mystery.")
    public MidiDevices pcpanel_list_midi_devices() {
        try {
            var devices = midiResource.listDevices();
            var available = !devices.isEmpty();
            var note = available
                    ? null
                    : "No MIDI inputs enumerated. In dev/JVM this means none are attached; in the native "
                            + "image javax.sound.midi returns no devices (known GraalVM limitation).";
            return new MidiDevices(available, note, devices);
        } catch (Throwable t) {
            log.debug("MIDI enumeration failed", t);
            return new MidiDevices(false, describe(t), List.of());
        }
    }

    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    public record DeviceSnapshot(boolean found, String error, DeviceSnapshotDto device) {
    }

    public record SerialPorts(boolean available, String error, List<SerialPortDto> ports) {
    }

    public record MidiDevices(boolean midiSubsystemAvailable, String note, List<MidiDeviceDto> devices) {
    }
}
