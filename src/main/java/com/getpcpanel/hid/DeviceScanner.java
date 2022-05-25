package com.getpcpanel.hid;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import com.getpcpanel.Main;
import com.getpcpanel.device.DeviceType;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public enum DeviceScanner implements HidServicesListener {
    INSTANCE;

    private final ConcurrentHashMap<String, DeviceCommunicationHandler> CONNECTED_DEVICE_MAP = new ConcurrentHashMap<>();
    private HidServices hidServices;

    public static DeviceCommunicationHandler getConnectedDevice(String key) {
        return INSTANCE.CONNECTED_DEVICE_MAP.get(key);
    }

    public static void start() {
        INSTANCE.doStart();
    }

    private void doStart() {
        hidServices = HidManager.getHidServices(buildSpecification());
        hidServices.addHidServicesListener(INSTANCE);
        log.info("Starting HID services.");
        hidServices.start();
        log.info("Enumerating attached devices...");
    }

    private static HidServicesSpecification buildSpecification() {
        var hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoShutdown(false);
        hidServicesSpecification.setAutoStart(false);
        hidServicesSpecification.setScanInterval(3000);
        hidServicesSpecification.setPauseInterval(2000);
        hidServicesSpecification.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL);
        return hidServicesSpecification;
    }

    public static void deviceAdded(@NonNull String key, @NonNull HidDevice device, DeviceType deviceType) {
        if (!device.isOpen()) {
            device.open();
        }
        var deviceHandler = new DeviceCommunicationHandler(key, device);
        INSTANCE.CONNECTED_DEVICE_MAP.put(key, deviceHandler);
        deviceHandler.start();
        Main.onDeviceConnected(key, deviceType);
    }

    public static void deviceRemoved(String key, HidDevice device) {
        if (key == null || device == null)
            throw new IllegalArgumentException("key or device cannot be null key: " + key + " device: " + device);
        if (INSTANCE.CONNECTED_DEVICE_MAP.remove(key) != null)
            Main.onDeviceDisconnected(key);
    }

    private static void foundPCPanel(HidDevice newPCPanel, DeviceType deviceType) {
        log.info("FOUND PCPANEL : {}", newPCPanel);
        try {
            deviceAdded(newPCPanel.getSerialNumber(), newPCPanel, deviceType);
        } catch (Exception e) {
            log.error("Unable to handle device added", e);
        }
    }

    private static void lostPCPanel(HidDevice lostPCPanel) {
        log.info("LOST PCPANEL : {}", lostPCPanel);
        try {
            deviceRemoved(lostPCPanel.getSerialNumber(), lostPCPanel);
        } catch (Exception e) {
            log.error("Unable to handle device disconnect", e);
        }
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        determineType(event).ifPresent(type -> foundPCPanel(event.getHidDevice(), type));
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        determineType(event).ifPresent(type -> lostPCPanel(event.getHidDevice()));
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
        determineType(event).ifPresent(type -> lostPCPanel(event.getHidDevice()));
    }

    private Optional<DeviceType> determineType(HidServicesEvent event) {
        for (var deviceType : DeviceType.ALL) {
            if (event.getHidDevice().isVidPidSerial(deviceType.getVid(), deviceType.getPid(), null))
                return Optional.of(deviceType);
        }
        return Optional.empty();
    }

    public static void close() {
        try {
            INSTANCE.hidServices.shutdown();
        } catch (Exception e) {
            log.error("Error occurred when closing device", e);
        }
    }
}
