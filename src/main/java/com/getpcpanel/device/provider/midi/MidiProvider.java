package com.getpcpanel.device.provider.midi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.DigitalInputSpec;
import com.getpcpanel.device.descriptor.DiscoveryMode;
import com.getpcpanel.device.io.MidiTransport;
import com.getpcpanel.device.io.MidiTransport.MidiConnection;
import com.getpcpanel.device.io.MidiTransport.MidiDeviceInfo;
import com.getpcpanel.device.io.MidiTransport.MidiMessage;
import com.getpcpanel.device.provider.DeviceProvider;
import com.getpcpanel.device.provider.midi.MidiProtocol.MidiEvent;
import com.getpcpanel.device.provider.midi.MidiProtocol.MidiKind;
import com.getpcpanel.hid.DeviceCommunicationHandler.ButtonPressEvent;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * The MIDI {@link DeviceProvider}: an auto-discovered controller whose faders/knobs (CONTROL_CHANGE)
 * drive analog actions and whose pads (NOTE_ON/NOTE_OFF) act as buttons, through the existing command
 * layer. A MIDI device is a lightless {@code GenericDevice} (MIDI output is Phase 6).
 *
 * <p>Discovery is {@code AUTO}: {@link #start()} (and {@link #rescan()}) enumerate MIDI input devices
 * via the {@link MidiTransport} and open each new one. A device's {@link DeviceDescriptor} starts
 * empty and <em>grows</em> as controls are first seen (like Deej learns its slider count): a new CC
 * adds an {@code AnalogInputSpec}, a new note adds a {@code DigitalInputSpec}, keyed by a stable index
 * from {@link MidiControlIndex}. CC and NOTE never share an index. Each message normalizes MIDI 0-127
 * to the canonical 0-255 domain and fires the same {@code KnobRotateEvent}/{@code ButtonPressEvent}
 * the {@code InputInterpreter} consumes, so a MIDI control is handled exactly like a PCPanel control.
 *
 * <p>All decoding lives in {@link MidiProtocol}; the MIDI subsystem is behind the mockable
 * {@link MidiTransport} so this provider is unit-tested with a fake transport and canned messages.
 * Resilient by construction: a bad message never kills the receiver, and a broken MIDI backend (an
 * empty device list / a failed open in a native image) degrades to "no MIDI devices" without
 * affecting PCPanel/Deej or startup.
 */
@Log4j2
@ApplicationScoped
public class MidiProvider implements DeviceProvider {
    public static final String PROVIDER_ID = "midi";
    public static final String DEVICE_KIND_ID = "midi";
    /** providerConfig key holding the MIDI device's display name (the rest are learned id->index). */
    public static final String CFG_NAME = "midi.name";

    @Inject MidiTransport transport;
    @Inject SaveService saveService;
    @Inject Event<Object> eventBus;

    private final Map<String, MidiDevice> devices = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public DiscoveryMode discoveryMode() {
        return DiscoveryMode.AUTO;
    }

    @Override
    public void start() {
        // MIDI is best-effort: a broken backend must never crash startup or the other providers.
        try {
            rescan();
        } catch (Throwable t) {
            log.warn("MIDI provider start failed (MIDI unavailable): {}", t.getMessage());
        }
    }

    @Override
    public void stop() {
        StreamEx.ofValues(devices).forEach(MidiDevice::close);
        devices.clear();
    }

    /** A stable device id for a MIDI device, used as the persistence + event key. */
    public static String deviceIdFor(String midiId) {
        return PROVIDER_ID + ":" + midiId;
    }

    public List<MidiDeviceInfo> listInputs() {
        try {
            return transport.listInputs();
        } catch (Throwable t) {
            log.warn("MIDI unavailable (cannot list inputs): {}", t.getMessage());
            return List.of();
        }
    }

    /** Lists detected MIDI inputs with their connected state (for the REST visibility endpoint). */
    public List<DetectedMidi> detected() {
        var connectedIds = new HashSet<String>();
        StreamEx.ofValues(devices).forEach(d -> connectedIds.add(d.midiId));
        return StreamEx.of(listInputs())
                       .map(i -> new DetectedMidi(deviceIdFor(i.id()), i.name(), connectedIds.contains(i.id())))
                       .toList();
    }

    /** A detected MIDI input device for the REST listing. */
    public record DetectedMidi(String id, String name, boolean connected) {
    }

    /**
     * Enumerates MIDI inputs and opens every one not already connected. Idempotent and safe to call
     * periodically. Also reconnects persisted MIDI devices (so their learned control indices and the
     * profile bound to them survive a restart) once they reappear.
     */
    public synchronized void rescan() {
        for (var info : listInputs()) {
            var deviceId = deviceIdFor(info.id());
            if (devices.containsKey(deviceId)) {
                continue;
            }
            try {
                connect(info.id(), info.name());
            } catch (Throwable t) {
                log.warn("Unable to open MIDI device {}: {}", info.name(), t.getMessage());
            }
        }
    }

    private void connect(String midiId, String name) {
        var deviceId = deviceIdFor(midiId);
        var persisted = persistedConfigFor(deviceId);
        var index = MidiControlIndex.fromPersisted(stripName(persisted));
        var device = new MidiDevice(deviceId, midiId, name, index);
        devices.put(deviceId, device);
        device.open();
    }

    @Nullable
    private Map<String, String> persistedConfigFor(String deviceId) {
        var save = saveService.get();
        if (save == null) {
            return null;
        }
        var ds = save.getDeviceSave(deviceId);
        return ds == null ? null : ds.getProviderConfig();
    }

    /** Strips the reserved name key, leaving only the learned id->index entries for rehydration. */
    @Nullable
    private static Map<String, String> stripName(@Nullable Map<String, String> cfg) {
        if (cfg == null) {
            return null;
        }
        var out = new LinkedHashMap<>(cfg);
        out.remove(CFG_NAME);
        return out;
    }

    /** Builds the descriptor for the controls learned so far on this device. */
    static DeviceDescriptor buildDescriptor(String deviceId, String name, List<AnalogInputSpec> analog, List<DigitalInputSpec> digital) {
        return new DeviceDescriptor(PROVIDER_ID, DEVICE_KIND_ID, name, analog, digital, List.of(), List.of(), null);
    }

    /** One connected MIDI device: owns its connection, its index assigner and the learned controls. */
    final class MidiDevice {
        private final String deviceId;
        private final String midiId;
        private final String name;
        private final MidiControlIndex index;
        // Insertion order = first-seen order; the descriptor is rebuilt from these on every change.
        private final Map<String, AnalogInputSpec> analog = new LinkedHashMap<>();
        private final Map<String, DigitalInputSpec> digital = new LinkedHashMap<>();
        private final Set<String> known = new HashSet<>();
        @Nullable private volatile MidiConnection connection;
        private volatile boolean connectedFired;

        MidiDevice(String deviceId, String midiId, String name, MidiControlIndex index) {
            this.deviceId = deviceId;
            this.midiId = midiId;
            this.name = name;
            this.index = index;
        }

        // synchronized on the same monitor as handle(): transport.open() wires the receiver, after which
        // the javax.sound.midi receiver thread can call handle() (which mutates analog/digital/known) at
        // any moment. Without this lock, rehydrateFromPersisted() + the initial descriptor()/fire would
        // race a concurrent handle() on those non-thread-safe collections.
        synchronized void open() {
            connection = transport.open(midiId, this::onMessage, this::onError);
            // Fire connect immediately with an (initially empty, possibly rehydrated) descriptor so
            // DeviceHolder builds the device; the descriptor then grows as controls arrive.
            rehydrateFromPersisted();
            connectedFired = true;
            eventBus.fire(new DeviceScanner.DeviceConnectedEvent(deviceId, null, descriptor()));
            persist();
        }

        /** Pre-populates specs for any persisted control indices so they render before being touched. */
        private void rehydrateFromPersisted() {
            var cfg = stripName(persistedConfigFor(deviceId));
            if (cfg == null) {
                return;
            }
            for (var id : cfg.keySet()) {
                var idx = index.indexOfId(id);
                if (idx == null) {
                    continue;
                }
                if (id.startsWith("cc")) {
                    analog.computeIfAbsent(id, k -> new AnalogInputSpec(idx, id, ccLabel(id), AnalogKind.KNOB, 0, MidiProtocol.RAW_MAX, false, null));
                } else if (id.startsWith("note")) {
                    digital.computeIfAbsent(id, k -> new DigitalInputSpec(idx, id, noteLabel(id), true));
                }
                known.add(id);
            }
        }

        DeviceDescriptor descriptor() {
            return buildDescriptor(deviceId, name, new ArrayList<>(analog.values()), new ArrayList<>(digital.values()));
        }

        /** Handles one inbound MIDI message. A bad/unknown message never kills the receiver. */
        void onMessage(MidiMessage msg) {
            try {
                var decoded = MidiProtocol.decode(msg.status(), msg.data1(), msg.data2());
                if (decoded == null) {
                    return; // pitch-bend / program-change / system message — ignore cleanly
                }
                handle(decoded);
            } catch (Throwable t) {
                log.error("Error handling MIDI message on {}", name, t);
            }
        }

        private synchronized void handle(MidiEvent e) {
            var idx = index.assign(e.kind(), e.channel(), e.number());
            var id = MidiControlIndex.idFor(e.kind(), e.channel(), e.number());
            var grew = ensureControl(e.kind(), id, idx);
            if (grew) {
                // The descriptor changed: re-notify so the UI re-renders and persist the new mapping.
                eventBus.fire(new DeviceScanner.DeviceConnectedEvent(deviceId, null, descriptor()));
                persist();
            }
            if (e.kind() == MidiKind.ANALOG) {
                eventBus.fire(new KnobRotateEvent(deviceId, idx, MidiProtocol.normalize7bit(e.value()), false));
            } else {
                eventBus.fire(new ButtonPressEvent(deviceId, idx, e.pressed()));
            }
        }

        /** Adds a spec for a newly-seen control; returns true if the descriptor grew. */
        private boolean ensureControl(MidiKind kind, String id, int idx) {
            if (!known.add(id)) {
                return false;
            }
            if (kind == MidiKind.ANALOG) {
                analog.put(id, new AnalogInputSpec(idx, id, ccLabel(id), AnalogKind.KNOB, 0, MidiProtocol.RAW_MAX, false, null));
            } else {
                digital.put(id, new DigitalInputSpec(idx, id, noteLabel(id), true));
            }
            return true;
        }

        private void persist() {
            var save = saveService.get();
            if (save == null) {
                return;
            }
            var ds = save.getDeviceSave(deviceId);
            if (ds == null) {
                ds = new DeviceSave(save, PROVIDER_ID, () -> com.getpcpanel.profile.dto.LightingConfig.createAllColor("#0065FF"));
                save.getDevices().put(deviceId, ds);
            }
            ds.setProviderId(PROVIDER_ID);
            ds.setDeviceKindId(DEVICE_KIND_ID);
            var cfg = new LinkedHashMap<String, String>();
            cfg.put(CFG_NAME, name);
            cfg.putAll(index.toPersisted());
            ds.setProviderConfig(cfg);
            saveService.save();
        }

        private void onError(Throwable t) {
            log.warn("MIDI error on {}: {}", name, t.getMessage());
            close();
            devices.remove(deviceId, this);
        }

        void close() {
            var c = connection;
            connection = null;
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable t) {
                    log.debug("Error closing MIDI connection {}", name, t);
                }
            }
            if (connectedFired) {
                connectedFired = false;
                eventBus.fire(new DeviceScanner.DeviceDisconnectedEvent(deviceId));
            }
        }
    }

    private static String ccLabel(String id) {
        return id.toUpperCase().replace("CC", "CC ").replace(".CH", " ch");
    }

    private static String noteLabel(String id) {
        return id.replace("note", "Note ").replace(".ch", " ch");
    }
}
