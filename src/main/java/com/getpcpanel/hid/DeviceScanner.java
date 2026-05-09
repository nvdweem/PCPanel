package com.getpcpanel.hid;

import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import com.getpcpanel.device.DeviceType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class DeviceScanner implements HidServicesListener {
    private final ConcurrentHashMap<String, DeviceCommunicationHandler> connectedDeviceMap = new ConcurrentHashMap<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final long HANDLER_JOIN_TIMEOUT_MS = 1000;
    @Inject Event<Object> eventBus;
    @Inject DeviceCommunicationHandlerFactory deviceCommunicationHandlerFactory;

    private HidServices hidServices;

    public DeviceCommunicationHandler getConnectedDevice(String key) {
        return connectedDeviceMap.get(key);
    }

    // Not @PostConstruct because the startup sequence needs to control when this runs
    public void onStart(@Observes StartupEvent ev) {
        try {
            init();
        } catch (Throwable e) {
            log.error("Failed to initialize HID services – device scanning will be unavailable: {}", e.getMessage(), e);
        }
    }

    public void onShutdown(@Observes ShutdownEvent event) {
        close();
    }

    public void init() {
        hidServices = HidManager.getHidServices(buildSpecification());
        hidServices.addHidServicesListener(this);
        log.info("Starting HID services.");
        hidServices.start();
        log.info("Enumerating attached devices....");

        if (!shuttingDown.compareAndSet(true, false)) {
            reconnectDevicesAfterRestart();
        }
    }

    private void reconnectDevicesAfterRestart() {
        StreamEx.of(hidServices.getAttachedHidDevices())
                .mapToEntry(this::determineType).flatMapValues(Optional::stream)
                .forKeyValue(this::foundPCPanel);
    }

    static HidServicesSpecification buildSpecification() {
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
        connectedDeviceMap.put(key, deviceHandler);
        deviceHandler.start();
        fireEvent(new DeviceConnectedEvent(key, deviceType));
    }

    public void deviceRemoved(String key, HidDevice device) {
        if (key == null || device == null)
            throw new IllegalArgumentException("serialNum or device cannot be null serialNum: " + key + " device: " + device);
        if (connectedDeviceMap.remove(key) != null)
            fireEvent(new DeviceDisconnectedEvent(key));
    }

    private void foundPCPanel(HidDevice newPCPanel, DeviceType deviceType) {
        log.info("FOUND PCPANEL : {}", newPCPanel);
        if (!newPCPanel.isOpen())
            newPCPanel.open();

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
        determineType(event.getHidDevice()).ifPresent(type -> foundPCPanel(event.getHidDevice(), type));
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        if (determineType(event.getHidDevice()).isPresent()) {
            lostPCPanel(event.getHidDevice());
        }
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
        if (determineType(event.getHidDevice()).isPresent()) {
            lostPCPanel(event.getHidDevice());
        }
    }

    private Optional<DeviceType> determineType(HidDevice device) {
        for (var deviceType : DeviceType.ALL) {
            if (device.isVidPidSerial(deviceType.getVid(), deviceType.getPid(), null))
                return Optional.of(deviceType);
        }
        return Optional.empty();
    }

    public void close() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }

        var handlers = new ArrayList<>(connectedDeviceMap.values());
        connectedDeviceMap.clear();
        for (var handler : handlers) {
            try {
                handler.stopGracefully(HANDLER_JOIN_TIMEOUT_MS);
            } catch (Exception e) {
                log.debug("Error while stopping handler during shutdown.", e);
            }
        }

        try {
            if (hidServices != null) {
                hidServices.removeHidServicesListener(this);
                hidServices.shutdown();
                hidServices = null;
            }
        } catch (Exception e) {
            log.error("Error occurred when closing device!", e);
        }
    }

    public void fireEvent(Object event) {
        if (!shuttingDown.get()) {
            eventBus.fire(event);
        }
    }

    public record DeviceConnectedEvent(String serialNum, DeviceType deviceType) {
    }

    public record DeviceDisconnectedEvent(String serialNum) {
    }
}
