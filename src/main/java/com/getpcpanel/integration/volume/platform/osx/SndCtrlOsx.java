package com.getpcpanel.integration.volume.platform.osx;

import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEFAULT_INPUT_DEVICE;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEFAULT_OUTPUT_DEVICE;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEVICES;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_MUTE;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_VOLUME_SCALAR;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.SCOPE_GLOBAL;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.SCOPE_INPUT;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.SCOPE_OUTPUT;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.SCOPE_WILDCARD;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioDeviceEvent;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.platform.process.OsxProcessHelper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * macOS sound control backed by CoreAudio. Controls device volume/mute and default devices.
 * Per-application volume/mute (including focus volume) uses CoreAudio process taps via
 * {@link OsxProcessTapService} on macOS 14.4+; macOS has no per-application sessions, so
 * {@link #getAllSessions()} stays empty.
 */
@Log4j2
@ApplicationScoped
@MacBuild
@RequiredArgsConstructor
class SndCtrlOsx implements ISndCtrl {
    private static final String DEFAULT = "default";
    private static final List<String> SYSTEM_APP_PREFIXES = List.of("/System/Library/", "/System/Cryptexes/", "/usr/libexec/");
    private static final Comparator<RunningApplication> APP_ORDER = Comparator.comparing(RunningApplication::name, String.CASE_INSENSITIVE_ORDER);
    private final CoreAudioWrapper wrapper;
    private final OsxProcessHelper processHelper;
    private final OsxProcessTapService tapService;
    private final Event<Object> eventBus;
    @GuardedBy("devices") private final Map<String, OsxAudioDevice> devices = new HashMap<>();
    @GuardedBy("devices") private final Map<Integer, List<CoreAudioWrapper.ListenerHandle>> deviceListeners = new HashMap<>();
    // The handles strongly reference the JNA callbacks; without them JNA's WeakHashMap lets the GC collect the
    // callback while CoreAudio still holds the function pointer.
    private final List<CoreAudioWrapper.ListenerHandle> systemListeners = new ArrayList<>();

    @PostConstruct
    public void init() {
        systemListeners.add(wrapper.addListener(CoreAudioLib.kAudioObjectSystemObject, PROP_DEVICES, SCOPE_GLOBAL, this::initDevices));
        systemListeners.add(wrapper.addListener(CoreAudioLib.kAudioObjectSystemObject, PROP_DEFAULT_OUTPUT_DEVICE, SCOPE_GLOBAL, this::initDevices));
        systemListeners.add(wrapper.addListener(CoreAudioLib.kAudioObjectSystemObject, PROP_DEFAULT_INPUT_DEVICE, SCOPE_GLOBAL, this::initDevices));
        systemListeners.removeIf(Objects::isNull);
        initDevices();
    }

    @PreDestroy
    public void destroy() {
        systemListeners.forEach(wrapper::removeListener);
        systemListeners.clear();
    }

    private void initDevices() {
        var events = new ArrayList<AudioDeviceEvent>();
        synchronized (devices) {
            var prev = new HashMap<>(devices);
            devices.clear();
            for (var device : wrapper.getDevices()) {
                devices.put(device.uid(), new OsxAudioDevice(eventBus, device));
            }

            var currentIds = StreamEx.ofValues(devices).map(OsxAudioDevice::deviceId).toSet();
            StreamEx.ofKeys(deviceListeners).remove(currentIds::contains).toList()
                    .forEach(id -> deviceListeners.remove(id).forEach(wrapper::removeListener));
            StreamEx.of(currentIds).remove(deviceListeners::containsKey).forEach(this::addDeviceListeners);

            StreamEx.ofValues(prev).remove(d -> devices.containsKey(d.id())).forEach(d -> events.add(new AudioDeviceEvent(d, EventType.REMOVED)));
            StreamEx.ofValues(devices).forEach(current -> {
                var previous = prev.get(current.id());
                if (previous == null) {
                    events.add(new AudioDeviceEvent(current, EventType.ADDED));
                } else if (previous.volume() != current.volume() || previous.muted() != current.muted()) {
                    events.add(new AudioDeviceEvent(current, EventType.CHANGED));
                }
            });
        }
        events.forEach(eventBus::fire);
        log.trace("Devices initialized: {}", devices());
    }

    private void addDeviceListeners(int deviceId) {
        var handles = new ArrayList<CoreAudioWrapper.ListenerHandle>();
        handles.add(wrapper.addListener(deviceId, PROP_VOLUME_SCALAR, SCOPE_WILDCARD, () -> refreshDevice(deviceId)));
        handles.add(wrapper.addListener(deviceId, PROP_MUTE, SCOPE_WILDCARD, () -> refreshDevice(deviceId)));
        handles.removeIf(Objects::isNull);
        deviceListeners.put(deviceId, handles);
    }

    private void refreshDevice(int deviceId) {
        List<OsxAudioDevice> matching;
        synchronized (devices) {
            matching = StreamEx.ofValues(devices).filter(d -> d.deviceId() == deviceId).toList();
        }
        for (var device : matching) {
            var scope = device.isOutput() ? SCOPE_OUTPUT : SCOPE_INPUT;
            var volume = wrapper.getVolume(deviceId, scope);
            var muted = wrapper.getMute(deviceId, scope);
            device.updateState(volume == null ? device.volume() : volume, muted == null ? device.muted() : muted);
        }
    }

    @Override
    public Map<String, AudioDevice> getDevicesMap() {
        synchronized (devices) {
            return new HashMap<>(devices);
        }
    }

    @Override
    public Collection<AudioDevice> devices() {
        synchronized (devices) {
            // Stable order for the UI: outputs first, then by name. A value-hashed Set reshuffles whenever a volume changes.
            return StreamEx.ofValues(devices).select(AudioDevice.class)
                           .sorted(Comparator.comparing(AudioDevice::isInput).thenComparing(AudioDevice::name, String.CASE_INSENSITIVE_ORDER))
                           .toCollection(LinkedHashSet::new);
        }
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        return List.of();
    }

    @Override
    public AudioDevice getDevice(String id) {
        synchronized (devices) {
            return devices.get(id);
        }
    }

    @Override
    public void setDeviceVolume(String deviceId, float volume) {
        var device = resolveDevice(deviceId);
        if (device == null) {
            log.debug("No device found for '{}', not setting volume", deviceId);
            return;
        }
        wrapper.setVolume(device.deviceId(), device.isOutput() ? SCOPE_OUTPUT : SCOPE_INPUT, volume);
    }

    @Override
    public void muteDevice(String deviceId, MuteType mute) {
        var device = resolveDevice(deviceId);
        if (device == null) {
            log.debug("No device found for '{}', not muting", deviceId);
            return;
        }
        var scope = device.isOutput() ? SCOPE_OUTPUT : SCOPE_INPUT;
        wrapper.setMute(device.deviceId(), scope, mute.convert(wrapper.getMute(device.deviceId(), scope)));
    }

    @Override
    public void setDefaultDevice(String deviceId) {
        var device = resolveDevice(deviceId);
        if (device == null) {
            log.debug("No device found for '{}', not setting default", deviceId);
            return;
        }
        wrapper.setDefaultDevice(device.deviceId(), device.isOutput());
    }

    @Override
    public void setProcessVolume(String fileName, @Nullable String device, float volume) {
        pidsFor(fileName).forEach(pid -> tapService.setVolume(pid, volume));
    }

    @Override
    public void setFocusVolume(float volume) {
        processHelper.foregroundPid().ifPresent(pid -> tapService.setVolume(pid, volume));
    }

    @Override
    public void muteProcesses(Set<String> fileNames, MuteType mute) {
        StreamEx.of(fileNames).flatCollection(this::pidsFor).forEach(pid -> tapService.mute(pid, mute));
    }

    /** PIDs whose executable matches, by base name (mirroring the Windows session match) or full path. */
    private List<Integer> pidsFor(String fileName) {
        var baseName = new File(fileName).getName();
        return StreamEx.of(getRunningApplications())
                       .filter(app -> app.file().getName().equalsIgnoreCase(baseName) || app.file().getPath().equalsIgnoreCase(fileName))
                       .map(RunningApplication::pid)
                       .toList();
    }

    @Override
    public String getFocusApplication() {
        var app = processHelper.getFrontmostApp();
        return app == null ? "" : StringUtils.firstNonBlank(app.executablePath(), app.name(), "");
    }

    @Override
    public List<RunningApplication> getRunningApplications() {
        var apps = StreamEx.of(processHelper.listApps())
                           .filter(OsxProcessHelper.RunningApp::foreground)
                           .map(app -> new RunningApplication(app.pid(), new File(app.executablePath()), StringUtils.defaultIfBlank(app.name(), appName(app.executablePath()))))
                           .sorted(APP_ORDER)
                           .toList();
        if (!apps.isEmpty()) {
            return apps;
        }
        return StreamEx.of(ProcessHandle.allProcesses())
                       .mapPartial(ph -> ph.info().command().map(cmd -> new RunningApplication((int) ph.pid(), new File(cmd), appName(cmd))))
                       .filter(app -> app.file().getPath().contains(".app/Contents/MacOS/"))
                       .remove(app -> StreamEx.of(SYSTEM_APP_PREFIXES).anyMatch(app.file().getPath()::startsWith))
                       .sorted(APP_ORDER)
                       .toList();
    }

    @Override
    public String defaultDeviceOnEmpty(String deviceId) {
        if (StringUtils.isNotBlank(deviceId) && !StringUtils.equals(DEFAULT, deviceId)) {
            return deviceId;
        }
        return StringUtils.defaultString(defaultPlayer());
    }

    @Override
    public @Nullable String defaultPlayer() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).findFirst(OsxAudioDevice::defaultOutput).map(AudioDevice::id).orElse(null);
        }
    }

    @Override
    public @Nullable String defaultRecorder() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).findFirst(OsxAudioDevice::defaultInput).map(AudioDevice::id).orElse(null);
        }
    }

    private @Nullable OsxAudioDevice resolveDevice(@Nullable String deviceId) {
        synchronized (devices) {
            if (StringUtils.isBlank(deviceId) || DEFAULT.equals(deviceId)) {
                return StreamEx.ofValues(devices).findFirst(OsxAudioDevice::defaultOutput).orElse(null);
            }
            return devices.get(deviceId);
        }
    }

    private static String appName(String command) {
        var file = new File(command);
        for (var parent = file.getParentFile(); parent != null; parent = parent.getParentFile()) {
            if (parent.getName().endsWith(".app")) {
                return StringUtils.removeEnd(parent.getName(), ".app");
            }
        }
        return file.getName();
    }
}
