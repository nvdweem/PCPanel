package com.getpcpanel.hid;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceFactory;
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
            save.createSaveForNewDevice(event.serialNum(), event.deviceType());
        var device = deviceFactory.build(event.serialNum(), save.getDeviceSave(event.serialNum()), event.descriptor());
        devices.put(event.serialNum(), device);
        outputInterpreter.sendInit(event.serialNum());
        device.setLighting(device.lightingConfig(), true);
        eventBus.fire(new DeviceFullyConnectedEvent(device));
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
