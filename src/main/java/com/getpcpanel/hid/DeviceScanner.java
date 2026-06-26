package com.getpcpanel.hid;

import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;
import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import com.getpcpanel.device.DescriptorFactory;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.DiscoveryMode;
import com.getpcpanel.device.provider.DeviceProvider;
import com.getpcpanel.util.OsxPermissionHelper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * The PCPanel HID {@link DeviceProvider}: discovers PCPanel devices over hid4java by VID/PID, opens
 * each exactly once, keeps retrying transient open failures, and fires a connect event carrying the
 * device's {@link DeviceDescriptor}. Its lifecycle is driven by
 * {@link com.getpcpanel.device.provider.DeviceProviderRegistry}.
 */
@Log4j2
@ApplicationScoped
public class DeviceScanner implements HidServicesListener, DeviceProvider {
    private final ConcurrentHashMap<String, DeviceCommunicationHandler> connectedDeviceMap = new ConcurrentHashMap<>();
    // PCPanels that are attached but failed to open (transient on Linux/Flatpak at startup). The reconcile thread
    // keeps retrying these; hid4java will not re-report a device that stays continuously plugged in, so without
    // this retry a failed initial open left the device invisible until the app was restarted.
    private final Set<String> failedToOpen = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final long HANDLER_JOIN_TIMEOUT_MS = 1000;
    private static final long RECONCILE_INTERVAL_MS = 3000;
    @Inject Event<Object> eventBus;
    @Inject DeviceCommunicationHandlerFactory deviceCommunicationHandlerFactory;

    private HidServices hidServices;
    private Thread reconcileThread;

    public DeviceCommunicationHandler getConnectedDevice(String key) {
        return connectedDeviceMap.get(key);
    }

    @Override
    public String id() {
        return DescriptorFactory.PROVIDER_ID;
    }

    @Override
    public DiscoveryMode discoveryMode() {
        return DiscoveryMode.AUTO;
    }

    @Override
    public void start() {
        try {
            init();
        } catch (Throwable e) {
            log.error("Failed to initialize HID services – device scanning will be unavailable: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
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

        startReconciliation();
        scheduleNoDeviceFoundCheck();
    }

    /**
     * Retries opening PCPanels that are attached but failed to open. The hid4java fixed-interval scanner only fires
     * {@link #hidDeviceAttached} for newly-appearing devices, so a panel that is continuously plugged in is never
     * re-reported after a failed initial open. On Linux/Flatpak the first open right after startup can fail
     * transiently (USB/udev/sandbox not settled yet), which previously left the device invisible until the app was
     * restarted. This loop re-enumerates and retries while any open is outstanding, then goes idle.
     */
    private void startReconciliation() {
        var t = new Thread(this::reconcileLoop, "pcpanel-device-reconcile");
        t.setDaemon(true);
        reconcileThread = t;
        t.start();
    }

    private void reconcileLoop() {
        while (!shuttingDown.get()) {
            try {
                Thread.sleep(RECONCILE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (shuttingDown.get() || hidServices == null || failedToOpen.isEmpty()) {
                continue;
            }
            try {
                StreamEx.of(hidServices.getAttachedHidDevices())
                        .mapToEntry(this::determineType).flatMapValues(Optional::stream)
                        .filterKeys(d -> !connectedDeviceMap.containsKey(d.getSerialNumber()))
                        .forKeyValue(this::foundPCPanel);
            } catch (Exception e) {
                log.debug("Device reconciliation scan failed", e);
            }
        }
    }

    /**
     * On macOS the HID device does not even enumerate without the Input Monitoring permission, so a missing
     * permission shows up as "no device found" rather than a failed open. Warn about it shortly after startup.
     */
    private void scheduleNoDeviceFoundCheck() {
        if (!SystemUtils.IS_OS_MAC) {
            return;
        }
        var checker = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (connectedDeviceMap.isEmpty() && !OsxPermissionHelper.isInputMonitoringGranted()) {
                log.warn("No PCPanel detected. macOS requires the Input Monitoring permission: " +
                        "System Settings > Privacy & Security > Input Monitoring > enable PCPanel, then restart PCPanel");
            }
        }, "pcpanel-mac-permission-check");
        checker.setDaemon(true);
        checker.start();
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
        var descriptor = DescriptorFactory.forType(deviceType);
        // A device can be reported twice on startup: once by reconnectDevicesAfterRestart() and once by the
        // scanner's initial hidDeviceAttached event. Each carries a different HidDevice instance for the same
        // physical device. On Linux opening an already-opened device fails, so the second
        // handler would run on an unopened device ("Device has not been opened"). Register atomically per key so
        // only the first event opens the device and creates a handler.
        var created = new DeviceCommunicationHandler[1];
        connectedDeviceMap.computeIfAbsent(key, k -> {
            if (!device.isOpen() && !device.open()) {
                // Log once per device; the reconcile thread keeps retrying, so we don't want to spam every interval.
                if (failedToOpen.add(k)) {
                    log.error("Unable to open device {}, it won't be possible to use the panel yet; will keep retrying", k);
                    if (SystemUtils.IS_OS_MAC && !OsxPermissionHelper.isInputMonitoringGranted()) {
                        log.error("macOS requires the Input Monitoring permission: " +
                                "System Settings > Privacy & Security > Input Monitoring > enable PCPanel");
                    } else if (SystemUtils.IS_OS_LINUX) {
                        // The device is visible but the hidraw node it opens (/dev/hidrawN) is root-owned unless a
                        // hidraw udev rule grants the logged-in user access. The .deb installs that rule;
                        // AppImage/Flatpak/manual installs add it themselves (the Flatpak too — --device=all exposes
                        // the node but cannot change its host ACL).
                        log.error("Linux needs a hidraw udev access rule for the PCPanel device — it is detected but " +
                                "cannot be opened. Add the KERNEL==\"hidraw*\", SUBSYSTEM==\"hidraw\", ATTRS{idVendor}==... " +
                                "lines (see linux.md / 70-pcpanel.rules), then run 'sudo udevadm control --reload-rules " +
                                "&& sudo udevadm trigger'.");
                    }
                } else {
                    log.debug("Retry to open device {} still failing", k);
                }
                return null;
            }
            return created[0] = deviceCommunicationHandlerFactory.build(k, device, descriptor);
        });
        if (created[0] != null) {
            failedToOpen.remove(key);
            log.info("Connected to PCPanel {} ({})", key, deviceType);
            created[0].start();
            fireEvent(new DeviceConnectedEvent(key, deviceType, descriptor));
        }
    }

    public void deviceRemoved(String key, HidDevice device) {
        if (key == null || device == null)
            throw new IllegalArgumentException("serialNum or device cannot be null serialNum: " + key + " device: " + device);
        // Stop retrying a panel that has been unplugged (it may never have opened, so it is not in connectedDeviceMap).
        failedToOpen.remove(key);
        if (connectedDeviceMap.remove(key) != null)
            fireEvent(new DeviceDisconnectedEvent(key));
    }

    private void foundPCPanel(HidDevice newPCPanel, DeviceType deviceType) {
        log.debug("Found PCPanel: {}", newPCPanel);
        try {
            // Opening happens inside deviceAdded so it is done exactly once per device, even when the same
            // device is reported by both the reconnect scan and the hidDeviceAttached event.
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

    @Override
    public void hidDataReceived(HidServicesEvent event) {
        // Unused: PCPanel reads its devices directly via DeviceCommunicationHandler rather than through
        // hid4java's auto-data-read. Required because hid4java 0.8 made this an abstract listener method.
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

        if (reconcileThread != null) {
            reconcileThread.interrupt();
            reconcileThread = null;
        }
        failedToOpen.clear();

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

    public record DeviceConnectedEvent(String serialNum, DeviceType deviceType, DeviceDescriptor descriptor) {
    }

    public record DeviceDisconnectedEvent(String serialNum) {
    }
}
