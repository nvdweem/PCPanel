package com.getpcpanel.midi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;

import com.getpcpanel.midi.VirtualMidiLibrary.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;

import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class MidiService {
    private final Map<String, Pointer> midiPorts = new ConcurrentHashMap<>();
    private final Map<String, Callback> callbacks = new ConcurrentHashMap<>();
    private static final int MAX_SYSEX_LENGTH = 1024;

    public String getDriverVersion() {
        try {
            var major = new IntByReference();
            var minor = new IntByReference();
            var release = new IntByReference();
            var build = new IntByReference();
            var version = VirtualMidiLibrary.INSTANCE.virtualMIDIGetVersion(major, minor, release, build);
            return String.format("%s (%d.%d.%d.%d)", version, major.getValue(), minor.getValue(), release.getValue(), build.getValue());
        } catch (Throwable e) {
            return "Unknown";
        }
    }

    public boolean createPort(String portId, String portName, BiConsumer<String, byte[]> dataReceiver) {
        if (midiPorts.containsKey(portId)) {
            log.warn("MIDI port already created for: {}", portId);
            return false;
        }

        try {
            Callback callback = (midiPort, data, length, clientContext) -> {
                if (dataReceiver != null && data != null && length > 0) {
                    var receivedData = data.getByteArray(0, length);
                    dataReceiver.accept(portId, receivedData);
                }
            };

            var port = VirtualMidiLibrary.INSTANCE.virtualMIDICreatePortEx2(
                    new WString(portName),
                    callback,
                    null,
                    MAX_SYSEX_LENGTH,
                    VirtualMidiLibrary.TE_VM_FLAGS_INSTANTIATE_BOTH
            );

            if (port == null) {
                var lastError = VirtualMidiLibrary.INSTANCE.virtualMIDIGetLastError();
                log.error("Failed to create virtual MIDI port. Error code: {}", lastError);
                return false;
            }

            midiPorts.put(portId, port);
            callbacks.put(portId, callback); // Keep reference to prevent GC
            log.info("Successfully created virtual MIDI port: {} with ID: {}", portName, portId);
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.error("teVirtualMIDI.dll not found. Please install virtualMIDI driver.", e);
            return false;
        } catch (Exception e) {
            log.error("Error creating virtual MIDI port", e);
            return false;
        }
    }

    public void sendControlChange(String portId, int channel, int control, int value) {
        var port = midiPorts.get(portId);
        if (port == null) {
            return;
        }

        var status = (byte) (0xB0 | (channel & 0x0F));
        var data = new byte[] { status, (byte) (control & 0x7F), (byte) (value & 0x7F) };
        sendData(port, data);
    }

    public void sendNoteOn(String portId, int channel, int note, int velocity) {
        var port = midiPorts.get(portId);
        if (port == null) {
            return;
        }

        var status = (byte) (0x90 | (channel & 0x0F));
        var data = new byte[] { status, (byte) (note & 0x7F), (byte) (velocity & 0x7F) };
        sendData(port, data);
    }

    public void sendNoteOff(String portId, int channel, int note, int velocity) {
        var port = midiPorts.get(portId);
        if (port == null) {
            return;
        }

        var status = (byte) (0x80 | (channel & 0x0F));
        var data = new byte[] { status, (byte) (note & 0x7F), (byte) (velocity & 0x7F) };
        sendData(port, data);
    }

    private void sendData(Pointer port, byte[] data) {
        var success = VirtualMidiLibrary.INSTANCE.virtualMIDISendData(port, data, data.length);
        if (!success) {
            log.error("Failed to send MIDI data");
        }
    }

    public void closePort(String portId) {
        var port = midiPorts.remove(portId);
        if (port != null) {
            VirtualMidiLibrary.INSTANCE.virtualMIDIClosePort(port);
            callbacks.remove(portId);
            log.info("Closed virtual MIDI port with ID: {}", portId);
        }
    }

    @PreDestroy
    public void closeAllPorts() {
        midiPorts.keySet().forEach(this::closePort);
    }
}
