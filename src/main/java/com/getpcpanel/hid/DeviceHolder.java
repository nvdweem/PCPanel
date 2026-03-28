package com.getpcpanel.hid;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceFactory;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.hid.DeviceScanner.DeviceConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class DeviceHolder {
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final SaveService saveService;
    @Inject DeviceFactory deviceFactory;
    private final OutputInterpreter outputInterpreter;
    private final Event<Object> eventPublisher;

    public Optional<Device> getDevice(String key) {
        return Optional.ofNullable(devices.get(key));
    }

    public int size() {
        return devices.size();
    }

    public Collection<Device> values() {
        return devices.values();
    }

    // @Order(HIGHEST_PRECEDENCE) // TODO
    public void deviceAdded(@Observes DeviceConnectedEvent event) {
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
        eventPublisher.fire(new DeviceFullyConnectedEvent(device));
    }

    // @Order // TODO
    public void onDeviceDisconnected(@Observes DeviceDisconnectedEvent event) {
        var device = devices.remove(event.serialNum());
        if (device != null) {
            Platform.runLater(device::disconnected);
        }
    }

    public void focusApplicationChanged(@Observes WindowFocusChangedEvent event) {
        devices.values().forEach(Device::focusApplicationChanged);
    }

    public void saveChanged(@Observes SaveEvent event) {
        devices.values().forEach(Device::saveChanged);
    }

    public Collection<Device> all() {
        return devices.values();
    }

    public record DeviceFullyConnectedEvent(Device device) {
    }
}
