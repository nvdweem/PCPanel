package com.getpcpanel.hid;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.device.DescriptorFactory;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceFactory;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@ApplicationScoped
public class DeviceHolder {
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    @Inject SaveService saveService;
    @Inject DeviceFactory deviceFactory;
    @Inject OutputInterpreter outputInterpreter;
    @Inject Event<Object> eventBus;

    public Optional<Device> getDevice(String key) {
        return Optional.ofNullable(devices.get(key));
    }

    public int size() {
        return devices.size();
    }

    public Collection<Device> values() {
        return devices.values();
    }

    @Priority(1)
    public void deviceAdded(@Observes DeviceScanner.DeviceConnectedEvent event) {
        var save = saveService.get();
        if (!save.getDevices().containsKey(event.serialNum()))
            save.createSaveForNewDevice(event.serialNum(), event.descriptor());
        var deviceSave = save.getDeviceSave(event.serialNum());
        // Self-identifying persistence (Phase 2): back-fill identity/capabilities from the just-
        // connected descriptor for legacy saves (migrated providerId only) or whenever the hardware
        // now reports something different. Persist so a later disconnect can still render the device.
        if (backfillIdentity(deviceSave, event.descriptor())) {
            saveService.save();
        }
        var descriptor = event.descriptor();
        var isPcPanel = DescriptorFactory.PROVIDER_ID.equals(descriptor.providerId());
        // Route construction by provider: PCPanel keeps its model subclasses (path byte-for-byte
        // unchanged); everything else gets a descriptor-only GenericDevice. The HID-only post-connect
        // steps (init packet + push lighting) run only for a lighting-capable PCPanel/HID device; a
        // lightless device (Deej) has no output channel and skips both.
        var device = isPcPanel
                ? deviceFactory.build(event.serialNum(), deviceSave, descriptor)
                : deviceFactory.buildGeneric(event.serialNum(), deviceSave, descriptor);
        // A descriptor-only device (MIDI/Deej) grows its descriptor as new controls are learned, which
        // re-fires DeviceConnectedEvent and rebuilds the device here with a zeroed rotation array. Carry
        // the previously-learned knob positions forward so prior controls don't snap back to 0.
        carryKnobRotationsForward(devices.get(event.serialNum()), device, descriptor);
        devices.put(event.serialNum(), device);
        if (isPcPanel && descriptor.globalLighting() != null) {
            outputInterpreter.sendInit(event.serialNum());
            device.setLighting(device.lightingConfig(), true);
        }
        eventBus.fire(new DeviceFullyConnectedEvent(device));
    }

    /**
     * Populates {@link DeviceSave}'s provider identity and capability snapshot from a live
     * descriptor. Pure (no I/O); returns {@code true} when anything changed so the caller can decide
     * whether to persist. Safe to call on the CDI event thread.
     */
    static boolean backfillIdentity(DeviceSave deviceSave, DeviceDescriptor descriptor) {
        var changed = false;
        if (!descriptor.providerId().equals(deviceSave.getProviderId())) {
            deviceSave.setProviderId(descriptor.providerId());
            changed = true;
        }
        if (!descriptor.deviceKindId().equals(deviceSave.getDeviceKindId())) {
            deviceSave.setDeviceKindId(descriptor.deviceKindId());
            changed = true;
        }
        if (!descriptor.equals(deviceSave.getCapabilities())) {
            deviceSave.setCapabilities(descriptor);
            changed = true;
        }
        return changed;
    }

    /**
     * Copies already-known analog knob positions from a device being replaced to its rebuilt successor
     * (same serial). Generic devices grow their descriptor as controls are learned, each grow rebuilding
     * the device with a fresh zeroed rotation array; without this, previously-learned controls would snap
     * back to 0 until physically moved again. Only genuinely-read indices (per {@code hasKnobRotation})
     * are carried, so a not-yet-read control stays unread on the successor too.
     */
    private static void carryKnobRotationsForward(@Nullable Device previous, Device next, DeviceDescriptor descriptor) {
        if (previous == null || previous == next) {
            return;
        }
        for (var input : descriptor.analogInputs()) {
            var i = input.index();
            if (previous.hasKnobRotation(i)) {
                next.setKnobRotation(i, previous.getKnobRotation(i));
            }
        }
    }

    public void onDeviceDisconnected(@Observes DeviceScanner.DeviceDisconnectedEvent event) {
        var device = devices.remove(event.serialNum());
        if (device != null) {
            device.disconnected();
        }
    }

    public void focusApplicationChanged(@Observes WindowFocusChangedEvent event) {
        devices.values().forEach(Device::focusApplicationChanged);
    }

    public void saveChanged(@Observes SaveService.SaveEvent event) {
        devices.values().forEach(Device::saveChanged);
    }

    public Collection<Device> all() {
        return devices.values();
    }

    private <T extends Command> EntryStream<DeviceAndDial, T> buildCommandStream(Class<T> clazz) {
        return StreamEx.of(all())
                       .mapToEntry(Device::getSerialNumber).invert()
                       .mapValues(d -> d.currentProfile())
                       .flatMapKeyValue((id, profile) -> EntryStream.of(profile.getDialData()).mapKeys(d -> new DeviceAndDial(id, d)))
                       .mapToEntry(Map.Entry::getKey, Map.Entry::getValue)
                       .flatMapValues(d -> Commands.cmds(d).stream())
                       .selectValues(clazz);
    }

    public <T extends Command> boolean hasCommandsOf(Class<T> clazz, Predicate<T> filter) {
        return buildCommandStream(clazz).values().anyMatch(filter);
    }

    public <T extends Command> void triggerCommandsOf(Class<T> clazz, Function<EntryStream<DeviceAndDial, T>, EntryStream<DeviceAndDial, T>> chain) {
        buildCommandStream(clazz)
                .chain(chain)
                .forKeyValue((idAndDial, cmd) -> getDevice(idAndDial.id()).ifPresent(device -> {
                    var current = device.getKnobRotation(idAndDial.dial());
                    eventBus.fire(new DeviceCommunicationHandler.KnobRotateEvent(idAndDial.id(), idAndDial.dial(), current, false));
                }));
    }

    public record DeviceAndDial(String id, int dial) {
    }

    public record DeviceFullyConnectedEvent(Device device) {
    }
}
