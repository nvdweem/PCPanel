package com.getpcpanel.integration.display.platform.windows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.display.DdcOffCode;
import com.getpcpanel.integration.display.DdcToggle;
import com.getpcpanel.integration.display.DisplayNames;
import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.sleepdetection.WindowsSystemEventService;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * All displays: asks the sleep-detection helper window to put the monitors to sleep (see
 * {@link WindowsSystemEventService#turnDisplaysOff()}).
 *
 * <p>Single monitors: switched over DDC/CI through {@code dxva2}. Each desktop output ({@code \\.\DISPLAYn}) is found
 * with {@code EnumDisplayDevices}, its {@code HMONITOR} with {@code MonitorFromRect} over the output's current
 * position, and the physical monitors behind that with {@code GetPhysicalMonitorsFromHMONITOR}. A monitor's id is its
 * device interface path, which survives restarts; the physical monitor handles are opened per call and destroyed
 * before it returns. A monitor is named by the model Windows Settings shows, which {@code DisplayConfigGetDeviceInfo}
 * reports per device path.
 */
@Log4j2
@Unremovable
@WindowsBuild
@ApplicationScoped
class WinDisplayPower implements DisplayPower {
    private static final byte POWER_MODE = (byte) 0xD6;

    @Inject WindowsSystemEventService systemEvents;
    /** Off code per monitor id; capabilities take a second or more to read and do not change. */
    private final Map<String, Integer> offCodes = new ConcurrentHashMap<>();
    private final DdcToggle toggle = new DdcToggle();
    /** Monitors whose capabilities did not come back this session; {@link #list()} does not ask them again. */
    private final Set<String> unreadable = ConcurrentHashMap.newKeySet();
    /** Runs the capability reads {@link #list()} starts, so the command thread rarely has to. */
    private final Executor warmer = r -> Thread.ofPlatform().daemon().name("ddc-capabilities").start(r);
    /** Set while a capability read started by {@link #list()} runs, so there is never more than one. */
    private final AtomicBoolean warming = new AtomicBoolean();

    @Override
    public void turnOff() {
        systemEvents.turnDisplaysOff();
    }

    @Override
    public synchronized List<DisplayInfo> list() {
        try {
            var displays = withMonitors(monitors -> monitors.stream().map(m -> new DisplayInfo(m.id(), m.name())).toList());
            var cold = displays.stream().map(DisplayInfo::id).filter(id -> !offCodes.containsKey(id) && !unreadable.contains(id)).toList();
            if (!cold.isEmpty() && warming.compareAndSet(false, true)) {
                warmer.execute(() -> {
                    try {
                        cold.forEach(this::warm);
                    } finally {
                        warming.set(false);
                    }
                });
            }
            return displays;
        } catch (Throwable e) {
            log.warn("Unable to list the displays", e);
            return List.of();
        }
    }

    @Override
    public synchronized void toggle(List<String> ids) {
        try {
            withMonitors(monitors -> {
                var byId = new LinkedHashMap<String, PhysicalMonitor>();
                monitors.stream().filter(m -> ids.contains(m.id())).forEach(m -> byId.putIfAbsent(m.id(), m));
                if (byId.size() < new HashSet<>(ids).size()) {
                    log.info("{} of the chosen displays are not connected", new HashSet<>(ids).size() - byId.size());
                }
                var modes = new LinkedHashMap<String, Integer>();
                byId.forEach((id, m) -> modes.put(id, powerMode(m)));
                var plan = toggle.plan(modes);
                for (var id : plan.ids()) {
                    var m = byId.get(id);
                    var code = plan.turnOff() ? offCode(m) : DdcOffCode.ON;
                    if (WinDxva2.INSTANCE.SetVCPFeature(m.handle(), POWER_MODE, code)) {
                        toggle.switched(id, !plan.turnOff());
                    } else {
                        log.warn("Unable to turn {} {}", m.name(), plan.turnOff() ? "off" : "on");
                    }
                }
                return null;
            });
        } catch (Throwable e) {
            log.warn("Unable to switch the displays {}", ids, e);
        }
    }

    /**
     * Reads the capabilities of monitor {@code id} unless they are known or did not come back before. Holds the lock
     * for this one monitor only, so a press waits for at most one capability read.
     */
    private synchronized void warm(String id) {
        if (offCodes.containsKey(id) || unreadable.contains(id)) {
            return;
        }
        try {
            withMonitors(monitors -> {
                monitors.stream().filter(m -> m.id().equals(id)).findFirst().ifPresent(this::offCode);
                return null;
            });
        } catch (Throwable e) {
            log.debug("Unable to read the capabilities of {}", id, e);
        }
    }

    /** The off code for {@code monitor}; remembered only once its capabilities were read. */
    private int offCode(PhysicalMonitor monitor) {
        var known = offCodes.get(monitor.id());
        if (known != null) {
            return known;
        }
        var caps = capabilities(monitor.handle());
        var code = DdcOffCode.choose(caps);
        if (caps != null) {
            offCodes.put(monitor.id(), code);
            unreadable.remove(monitor.id());
        } else {
            unreadable.add(monitor.id());
        }
        return code;
    }

    /** The power mode {@code monitor} reports; null when it does not answer. */
    private static @Nullable Integer powerMode(PhysicalMonitor monitor) {
        var current = new IntByReference();
        if (WinDxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(monitor.handle(), POWER_MODE, null, current, null)) {
            return current.getValue();
        }
        log.info("{} does not answer over DDC/CI: is DDC/CI on in its menu?", monitor.name());
        return null;
    }

    /** The display number in an output name such as {@code \\.\DISPLAY2}, or 0 when it has none. */
    static int displayNumber(String outputName) {
        var digits = StringUtils.getDigits(StringUtils.substringAfterLast(outputName.toUpperCase(Locale.ROOT), "DISPLAY"));
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    /**
     * "DELL S3221QS · Display 2 · 2560×1440 · main": the model Windows Settings shows, when the monitor reports one; the
     * physical monitors sharing one output (a cloned desktop) are numbered after the resolution.
     */
    static String name(@Nullable String model, String outputName, int width, int height, int index, int count, boolean main) {
        return DisplayNames.format(model, displayNumber(outputName), width, height, count > 1 ? Integer.toString(index + 1) : "", main ? "main" : "");
    }

    /** The key {@link #friendlyNames()} uses for a device path. */
    static String pathKey(String devicePath) {
        return devicePath.toLowerCase(Locale.ROOT);
    }

    /**
     * Model name per {@link #pathKey device path} of every active monitor, as Windows Settings shows it; empty when
     * the display configuration cannot be read. Monitors that report no name are left out.
     */
    private static Map<String, String> friendlyNames() {
        try {
            var user32 = WinDisplayUser32.INSTANCE;
            var pathCount = new IntByReference();
            var modeCount = new IntByReference();
            for (var attempt = 0; attempt < 3; attempt++) {
                if (user32.GetDisplayConfigBufferSizes(WinDisplayUser32.QDC_ONLY_ACTIVE_PATHS, pathCount, modeCount) != WinDisplayUser32.ERROR_SUCCESS
                        || pathCount.getValue() <= 0) {
                    return Map.of();
                }
                var paths = new Memory((long) WinDisplayUser32.PATH_INFO_SIZE * pathCount.getValue());
                var modes = new Memory((long) WinDisplayUser32.MODE_INFO_SIZE * Math.max(modeCount.getValue(), 1));
                paths.clear();
                modes.clear();
                var result = user32.QueryDisplayConfig(WinDisplayUser32.QDC_ONLY_ACTIVE_PATHS, pathCount, paths, modeCount, modes, null);
                if (result == WinDisplayUser32.ERROR_INSUFFICIENT_BUFFER) {
                    continue; // a display came in between the two calls
                }
                if (result != WinDisplayUser32.ERROR_SUCCESS) {
                    return Map.of();
                }
                var names = new LinkedHashMap<String, String>();
                var request = new Memory(WinDisplayUser32.TARGET_NAME_SIZE);
                for (var i = 0; i < pathCount.getValue(); i++) {
                    var path = (long) i * WinDisplayUser32.PATH_INFO_SIZE;
                    request.clear();
                    request.setInt(WinDisplayUser32.DEVICE_INFO_TYPE_OFFSET, WinDisplayUser32.DEVICE_INFO_GET_TARGET_NAME);
                    request.setInt(WinDisplayUser32.DEVICE_INFO_SIZE_OFFSET, WinDisplayUser32.TARGET_NAME_SIZE);
                    request.setLong(WinDisplayUser32.DEVICE_INFO_ADAPTER_OFFSET, paths.getLong(path + WinDisplayUser32.PATH_TARGET_ADAPTER_OFFSET));
                    request.setInt(WinDisplayUser32.DEVICE_INFO_ID_OFFSET, paths.getInt(path + WinDisplayUser32.PATH_TARGET_ID_OFFSET));
                    if (user32.DisplayConfigGetDeviceInfo(request) != WinDisplayUser32.ERROR_SUCCESS) {
                        continue;
                    }
                    var friendly = request.getWideString(WinDisplayUser32.TARGET_NAME_FRIENDLY_OFFSET).strip();
                    var devicePath = request.getWideString(WinDisplayUser32.TARGET_NAME_PATH_OFFSET);
                    if (!friendly.isEmpty() && !devicePath.isBlank()) {
                        names.putIfAbsent(pathKey(devicePath), friendly);
                    }
                }
                return names;
            }
        } catch (Throwable e) {
            log.debug("Unable to read the monitor names", e);
        }
        return Map.of();
    }

    /** Id for the {@code index}th physical monitor of an output: its device path, else output name and index. */
    static String id(String outputName, List<String> devicePaths, int index) {
        return index < devicePaths.size() && StringUtils.isNotBlank(devicePaths.get(index)) ? devicePaths.get(index) : outputName + "#" + index;
    }

    private interface MonitorsCall<T> {
        T apply(List<PhysicalMonitor> monitors);
    }

    private record PhysicalMonitor(String id, String name, @Nullable Pointer handle) {
    }

    /** Opens every physical monitor on the desktop, runs {@code call} on them and destroys their handles again. */
    private static <T> T withMonitors(MonitorsCall<T> call) {
        var arrays = new ArrayList<Memory>();
        var counts = new ArrayList<Integer>();
        try {
            var monitors = new ArrayList<PhysicalMonitor>();
            var models = friendlyNames();
            var device = new Memory(WinDisplayUser32.DISPLAY_DEVICE_SIZE);
            for (var i = 0; ; i++) {
                device.clear();
                device.setInt(0, WinDisplayUser32.DISPLAY_DEVICE_SIZE);
                if (!WinDisplayUser32.INSTANCE.EnumDisplayDevicesW(null, i, device, 0)) {
                    break;
                }
                if ((device.getInt(WinDisplayUser32.DISPLAY_DEVICE_STATE_FLAGS_OFFSET) & WinDisplayUser32.DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) == 0) {
                    continue;
                }
                var output = device.getWideString(WinDisplayUser32.DISPLAY_DEVICE_NAME_OFFSET);
                var main = (device.getInt(WinDisplayUser32.DISPLAY_DEVICE_STATE_FLAGS_OFFSET) & WinDisplayUser32.DISPLAY_DEVICE_PRIMARY_DEVICE) != 0;
                var mode = new Memory(WinDisplayUser32.DEVMODE_SIZE);
                mode.clear();
                mode.setShort(WinDisplayUser32.DEVMODE_SIZE_OFFSET, (short) WinDisplayUser32.DEVMODE_SIZE);
                if (!WinDisplayUser32.INSTANCE.EnumDisplaySettingsExW(new WString(output), WinDisplayUser32.ENUM_CURRENT_SETTINGS, mode, 0)) {
                    continue;
                }
                var x = mode.getInt(WinDisplayUser32.DEVMODE_POSITION_OFFSET);
                var y = mode.getInt(WinDisplayUser32.DEVMODE_POSITION_OFFSET + 4);
                var width = mode.getInt(WinDisplayUser32.DEVMODE_PELS_WIDTH_OFFSET);
                var height = mode.getInt(WinDisplayUser32.DEVMODE_PELS_HEIGHT_OFFSET);
                var hMonitor = monitorAt(x, y, width, height);
                if (hMonitor == null) {
                    continue;
                }
                var count = new IntByReference();
                if (!WinDxva2.INSTANCE.GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor, count) || count.getValue() <= 0) {
                    continue;
                }
                var array = new Memory((long) WinDxva2.PHYSICAL_MONITOR_SIZE * count.getValue());
                array.clear();
                if (!WinDxva2.INSTANCE.GetPhysicalMonitorsFromHMONITOR(hMonitor, count.getValue(), array)) {
                    continue;
                }
                arrays.add(array);
                counts.add(count.getValue());
                var paths = devicePaths(output);
                for (var k = 0; k < count.getValue(); k++) {
                    // 0 is a valid physical monitor handle (often the first one's), which JNA reads as null
                    var handle = array.getPointer((long) k * WinDxva2.PHYSICAL_MONITOR_SIZE);
                    var id = id(output, paths, k);
                    var name = name(models.get(pathKey(id)), output, width, height, k, count.getValue(), main);
                    monitors.add(new PhysicalMonitor(id, name, handle));
                }
            }
            return call.apply(monitors);
        } finally {
            for (var i = 0; i < arrays.size(); i++) {
                WinDxva2.INSTANCE.DestroyPhysicalMonitors(counts.get(i), arrays.get(i));
            }
        }
    }

    private static @Nullable Pointer monitorAt(int x, int y, int width, int height) {
        var rect = new Memory(16);
        rect.setInt(0, x);
        rect.setInt(4, y);
        rect.setInt(8, x + Math.max(width, 1));
        rect.setInt(12, y + Math.max(height, 1));
        return WinDisplayUser32.INSTANCE.MonitorFromRect(rect, WinDisplayUser32.MONITOR_DEFAULTTONULL);
    }

    /** Device interface paths of the active monitors on output {@code output}, in Windows' order. */
    private static List<String> devicePaths(String output) {
        var result = new ArrayList<String>();
        var device = new Memory(WinDisplayUser32.DISPLAY_DEVICE_SIZE);
        var name = new WString(output);
        for (var j = 0; ; j++) {
            device.clear();
            device.setInt(0, WinDisplayUser32.DISPLAY_DEVICE_SIZE);
            if (!WinDisplayUser32.INSTANCE.EnumDisplayDevicesW(name, j, device, WinDisplayUser32.EDD_GET_DEVICE_INTERFACE_NAME)) {
                return result;
            }
            if ((device.getInt(WinDisplayUser32.DISPLAY_DEVICE_STATE_FLAGS_OFFSET) & WinDisplayUser32.DISPLAY_DEVICE_ACTIVE) != 0) {
                result.add(device.getWideString(WinDisplayUser32.DISPLAY_DEVICE_ID_OFFSET));
            }
        }
    }

    private static @Nullable String capabilities(Pointer handle) {
        var length = new IntByReference();
        if (!WinDxva2.INSTANCE.GetCapabilitiesStringLength(handle, length) || length.getValue() <= 0) {
            return null;
        }
        var buffer = new Memory(length.getValue() + 1L);
        buffer.clear();
        if (!WinDxva2.INSTANCE.CapabilitiesRequestAndCapabilitiesReply(handle, buffer, length.getValue())) {
            return null;
        }
        return buffer.getString(0, "US-ASCII");
    }
}
