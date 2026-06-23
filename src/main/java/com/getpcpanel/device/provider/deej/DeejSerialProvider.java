package com.getpcpanel.device.provider.deej;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.DiscoveryMode;
import com.getpcpanel.device.io.SerialTransport;
import com.getpcpanel.device.io.SerialTransport.SerialConnection;
import com.getpcpanel.device.provider.DeviceProvider;
import com.getpcpanel.device.provider.deej.DeejProtocol.NoiseReduction;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

/**
 * The Deej serial {@link DeviceProvider}: a manually-added (port + baud) volume mixer. Deej is
 * strictly device-&gt;host (no lights, no buttons, no discovery), so a Deej device is a lightless
 * {@code GenericDevice} whose sliders drive the existing command layer.
 *
 * <p>On the first valid line from a port it learns the slider count, builds a {@link DeviceDescriptor}
 * (N {@code SLIDER} analog inputs, source range 0-1023) and fires a connect event. On each
 * subsequent valid line it normalizes raw 0-1023 -&gt; 0-255 and fires a {@link KnobRotateEvent} per
 * slider — but only when the deej dead-band says the value changed, so the bus is not flooded at
 * ~100 Hz. The {@code InputInterpreter}/{@code CommandDispatcher} then handle it exactly like a
 * PCPanel knob.
 *
 * <p>All protocol logic lives in {@link DeejProtocol}; the serial port is behind the mockable
 * {@link SerialTransport} so this provider is unit-tested with a fake transport and canned lines.
 */
@Log4j2
@ApplicationScoped
public class DeejSerialProvider implements DeviceProvider {
    public static final String PROVIDER_ID = "deej";
    public static final String DEVICE_KIND_ID = "deej";
    public static final int DEFAULT_BAUD = 9600;
    public static final String CFG_PORT = "port";
    public static final String CFG_BAUD = "baud";
    public static final String CFG_NOISE = "noise";

    @Inject SerialTransport transport;
    @Inject SaveService saveService;
    @Inject Event<Object> eventBus;

    private final Map<String, DeejDevice> devices = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public DiscoveryMode discoveryMode() {
        return DiscoveryMode.MANUAL;
    }

    @Override
    public void start() {
        reconnectPersisted();
    }

    @Override
    public void stop() {
        StreamEx.ofValues(devices).forEach(DeejDevice::close);
        devices.clear();
    }

    /** A stable device id for a Deej device on {@code portName}, used as the persistence key. */
    public static String deviceIdFor(String portName) {
        return PROVIDER_ID + ":" + portName;
    }

    public List<SerialTransport.PortInfo> listPorts() {
        return transport.listPorts();
    }

    /** Reconnects every persisted Deej device on startup (best-effort; a failure is logged, not fatal). */
    private void reconnectPersisted() {
        var save = saveService.get();
        if (save == null) {
            return;
        }
        StreamEx.of(save.getDevices().values())
                .filter(ds -> PROVIDER_ID.equals(ds.getProviderId()))
                .map(DeviceSave::getProviderConfig)
                .nonNull()
                .forEach(cfg -> {
                    var port = cfg.get(CFG_PORT);
                    if (port == null) {
                        return;
                    }
                    var baud = parseBaud(cfg.get(CFG_BAUD));
                    var noise = NoiseReduction.fromString(cfg.get(CFG_NOISE));
                    try {
                        connect(port, baud, noise, false);
                    } catch (Exception e) {
                        log.warn("Unable to reconnect persisted Deej device on {}: {}", port, e.getMessage());
                    }
                });
    }

    /**
     * Manually adds (or reconnects) a Deej device by serial port and baud. Persists the connection
     * params so the device is reconnected on the next startup. Returns the stable device id.
     */
    public synchronized String addManual(String portName, @Nullable Integer baud, @Nullable String noiseLevel) {
        var resolvedBaud = baud == null ? DEFAULT_BAUD : baud;
        var noise = NoiseReduction.fromString(noiseLevel);
        var id = connect(portName, resolvedBaud, noise, true);
        return id;
    }

    public synchronized void removeManual(String deviceId) {
        var device = devices.remove(deviceId);
        // close() already fires the disconnect for a connected device; only fire the fallback ourselves
        // when it didn't (never-connected device, or no such device) so we emit exactly one event.
        var firedByClose = device != null && device.close();
        var save = saveService.get();
        if (save != null && save.getDevices().remove(deviceId) != null) {
            saveService.save();
        }
        if (!firedByClose) {
            eventBus.fire(new DeviceScanner.DeviceDisconnectedEvent(deviceId));
        }
    }

    private String connect(String portName, int baud, NoiseReduction noise, boolean persist) {
        var deviceId = deviceIdFor(portName);
        // Replace any existing connection to the same port.
        var existing = devices.remove(deviceId);
        if (existing != null) {
            existing.close();
        }
        var device = new DeejDevice(deviceId, portName, baud, noise);
        // Register before open() so the reader thread's onError() can find and remove this entry, but
        // undo the registration if open() fails (busy/unplugged port) so a dead device isn't left behind.
        devices.put(deviceId, device);
        try {
            device.open();
        } catch (RuntimeException e) {
            devices.remove(deviceId, device);
            throw e;
        }
        if (persist) {
            persist(deviceId, portName, baud, noise);
        }
        return deviceId;
    }

    private void persist(String deviceId, String portName, int baud, NoiseReduction noise) {
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
        ds.setProviderConfig(Map.of(CFG_PORT, portName, CFG_BAUD, String.valueOf(baud), CFG_NOISE, noise.name().toLowerCase()));
        saveService.save();
    }

    private static int parseBaud(@Nullable String s) {
        if (s == null) {
            return DEFAULT_BAUD;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_BAUD;
        }
    }

    /** Builds the descriptor for a learned slider count. */
    static DeviceDescriptor buildDescriptor(String deviceId, int sliderCount) {
        var sliders = IntStreamEx.range(0, sliderCount)
                                 .mapToObj(i -> new AnalogInputSpec(i, "slider" + i, "S" + (i + 1), AnalogKind.SLIDER,
                                         0, DeejProtocol.RAW_MAX, false, null))
                                 .toList();
        return new DeviceDescriptor(PROVIDER_ID, DEVICE_KIND_ID, "Deej",
                sliders, List.of(), List.of(), List.of(), null);
    }

    Optional<DeejDevice> device(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    /** One connected Deej device: owns its serial connection and the per-slider dead-band state. */
    final class DeejDevice {
        private final String deviceId;
        private final String portName;
        private final int baud;
        private final NoiseReduction noise;
        @Nullable private volatile SerialConnection connection;
        @Nullable private int[] lastRaw; // null until the first valid line learns the slider count
        // AtomicBoolean (not a plain volatile): close() is reachable concurrently from the serial reader
        // thread (onError) and a caller thread (removeManual/stop/connect-replace); the CAS makes the
        // "fire disconnect exactly once" decision atomic.
        private final java.util.concurrent.atomic.AtomicBoolean connectedFired = new java.util.concurrent.atomic.AtomicBoolean(false);

        DeejDevice(String deviceId, String portName, int baud, NoiseReduction noise) {
            this.deviceId = deviceId;
            this.portName = portName;
            this.baud = baud;
            this.noise = noise;
        }

        void open() {
            connection = transport.open(portName, baud, this::onLine, this::onError);
        }

        /** Handles one raw inbound line. Garbage lines are skipped; one bad line never kills the reader. */
        void onLine(String line) {
            try {
                var values = DeejProtocol.parse(line);
                if (values == null) {
                    return; // garbage / partial — skip
                }
                if (lastRaw == null) {
                    learn(values);
                    return;
                }
                emitChanges(values);
            } catch (Throwable t) {
                log.error("Error handling Deej line on {}", portName, t);
            }
        }

        private synchronized void learn(int[] values) {
            lastRaw = values.clone();
            var descriptor = buildDescriptor(deviceId, values.length);
            connectedFired.set(true);
            // Fire connect first so DeviceHolder builds the device, then emit the initial values so
            // the command layer/UI reflect the starting slider positions.
            eventBus.fire(new DeviceScanner.DeviceConnectedEvent(deviceId, null, descriptor));
            for (var i = 0; i < values.length; i++) {
                eventBus.fire(new KnobRotateEvent(deviceId, i, DeejProtocol.normalize(values[i]), true));
            }
        }

        private void emitChanges(int[] values) {
            var prev = lastRaw;
            if (prev == null) {
                return;
            }
            var count = Math.min(values.length, prev.length);
            for (var i = 0; i < count; i++) {
                if (DeejProtocol.significantlyDifferentRaw(prev[i], values[i], noise)) {
                    prev[i] = values[i];
                    eventBus.fire(new KnobRotateEvent(deviceId, i, DeejProtocol.normalize(values[i]), false));
                }
            }
        }

        private void onError(Throwable t) {
            log.warn("Deej serial error on {}: {}", portName, t.getMessage());
            close();
            devices.remove(deviceId, this);
        }

        /** Closes the port and fires a single disconnect event iff this device had connected. Returns
         *  whether it fired the disconnect, so callers don't double-fire. */
        boolean close() {
            var c = connection;
            connection = null;
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                    log.debug("Error closing Deej connection {}", portName, e);
                }
            }
            if (connectedFired.compareAndSet(true, false)) {
                eventBus.fire(new DeviceScanner.DeviceDisconnectedEvent(deviceId));
                return true;
            }
            return false;
        }
    }
}
