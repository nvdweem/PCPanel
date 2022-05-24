package hid;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import main.DeviceType;
import main.Window;
import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class DeviceScanner implements HidServicesListener {
    public static final ConcurrentHashMap<String, DeviceCommunicationHandler> CONNECTED_DEVICE_MAP = new ConcurrentHashMap<>();
    private static HidServices hidServices;

    public static void start() {
        hidServices = HidManager.getHidServices(buildSpecification());
        hidServices.addHidServicesListener(new DeviceScanner());
        log.info("Starting HID services.");
        hidServices.start();
        log.info("Enumerating attached devices...");
    }

    private static HidServicesSpecification buildSpecification() {
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
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
        DeviceCommunicationHandler deviceHandler = new DeviceCommunicationHandler(key, device);
        CONNECTED_DEVICE_MAP.put(key, deviceHandler);
        deviceHandler.start();
        Window.onDeviceConnected(key, deviceType);
    }

    public static void deviceRemoved(String key, HidDevice device) {
        if (key == null || device == null)
            throw new IllegalArgumentException("key or device cannot be null key: " + key + " device: " + device);
        DeviceCommunicationHandler old = CONNECTED_DEVICE_MAP.remove(key);
        if (old != null)
            Window.onDeviceDisconnected(key);
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
            hidServices.shutdown();
        } catch (Exception e) {
            log.error("Error occurred when closing device", e);
        }
    }
}
