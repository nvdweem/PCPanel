package com.getpcpanel.hid;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceFactory;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Service
@RequiredArgsConstructor
public class DeviceHolder {
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final SaveService saveService;
    @Autowired @Lazy @Setter private DeviceFactory deviceFactory;
    private final OutputInterpreter outputInterpreter;

    public Device getDevice(String key) {
        return devices.get(key);
    }

    public int size() {
        return devices.size();
    }

    public Collection<Device> values() {
        return devices.values();
    }

    @EventListener
    @Order(HIGHEST_PRECEDENCE)
    public void deviceAdded(DeviceScanner.DeviceConnectedEvent event) {
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
    }

    @Order
    @EventListener
    public void onDeviceDisconnected(DeviceScanner.DeviceDisconnectedEvent event) {
        var device = devices.remove(event.serialNum());
        if (device != null) {
            Platform.runLater(device::disconnected);
        }
    }

    @EventListener(WindowFocusChangedEvent.class)
    public void focusApplicationChanged() {
        devices.values().forEach(Device::focusApplicationChanged);
    }

    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        devices.values().forEach(Device::saveChanged);
    }

    public Collection<Device> all() {
        return devices.values();
    }
}
