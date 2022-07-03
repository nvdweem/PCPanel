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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.DeviceType;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class DeviceScanner implements HidServicesListener {
    private final ConcurrentHashMap<String, DeviceCommunicationHandler> CONNECTED_DEVICE_MAP = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    @Autowired @Lazy @Setter private DeviceCommunicationHandlerFactory deviceCommunicationHandlerFactory;

    private HidServices hidServices;

    public DeviceCommunicationHandler getConnectedDevice(String key) {
        return CONNECTED_DEVICE_MAP.get(key);
    }

    // Not @PostConstruct because the HomePage must have loaded before
    public void init() {
        hidServices = HidManager.getHidServices(buildSpecification());
        hidServices.addHidServicesListener(this);
        log.info("Starting HID services.");
        hidServices.start();
        log.info("Enumerating attached devices...");
    }

    private HidServicesSpecification buildSpecification() {
        var hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoShutdown(false);
        hidServicesSpecification.setAutoStart(false);
        hidServicesSpecification.setScanInterval(3000);
        hidServicesSpecification.setPauseInterval(2000);
        hidServicesSpecification.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL);
        return hidServicesSpecification;
    }

    public void deviceAdded(@NonNull String key, @NonNull HidDevice device, DeviceType deviceType) {
        if (!device.isOpen()) {
            if (!device.open()) {
                log.error("Unable to open device, it won't be possible to use the panel");
            }
        }
        var deviceHandler = deviceCommunicationHandlerFactory.build(key, device, deviceType);
        CONNECTED_DEVICE_MAP.put(key, deviceHandler);
        deviceHandler.start();
        eventPublisher.publishEvent(new DeviceConnectedEvent(key, deviceType));
    }

    public void deviceRemoved(String key, HidDevice device) {
        if (key == null || device == null)
            throw new IllegalArgumentException("serialNum or device cannot be null serialNum: " + key + " device: " + device);
        if (CONNECTED_DEVICE_MAP.remove(key) != null)
            eventPublisher.publishEvent(new DeviceDisconnectedEvent(key));
    }

    private void foundPCPanel(HidDevice newPCPanel, DeviceType deviceType) {
        log.info("FOUND PCPANEL : {}", newPCPanel);
        try {
            deviceAdded(newPCPanel.getSerialNumber(), newPCPanel, deviceType);
        } catch (Exception e) {
            log.error("Unable to handle device added", e);
        }
    }

    private void lostPCPanel(HidDevice lostPCPanel) {
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

    public void close() {
        try {
            hidServices.shutdown();
        } catch (Exception e) {
            log.error("Error occurred when closing device", e);
        }
    }

    public record DeviceConnectedEvent(String serialNum, DeviceType deviceType) {
    }

    public record DeviceDisconnectedEvent(String serialNum) {
    }
}
