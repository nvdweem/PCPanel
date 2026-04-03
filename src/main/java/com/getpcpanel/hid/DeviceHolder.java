package com.getpcpanel.hid;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceFactory;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;

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
        Device device;
        var save = saveService.get();
        if (!save.getDevices().containsKey(event.serialNum()))
            save.createSaveForNewDevice(event.serialNum(), event.deviceType());
        if (event.deviceType() == DeviceType.PCPANEL_RGB) {
            device = deviceFactory.buildRgb(event.serialNum(), save.getDeviceSave(event.serialNum()));
        } else if (event.deviceType() == DeviceType.PCPANEL_MINI) {
            device = deviceFactory.buildMini(event.serialNum(), save.getDeviceSave(event.serialNum()));
        } else if (event.deviceType() == DeviceType.PCPANEL_PRO) {
            device = deviceFactory.buildPro(event.serialNum(), save.getDeviceSave(event.serialNum()));
        } else {
            throw new IllegalArgumentException("unknown devicetype: " + event.deviceType().name());
        }
        devices.put(event.serialNum(), device);
        outputInterpreter.sendInit(event.serialNum());
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

    public record DeviceFullyConnectedEvent(Device device) {
    }
}
