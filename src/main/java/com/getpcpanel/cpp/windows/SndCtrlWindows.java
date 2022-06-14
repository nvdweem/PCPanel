package com.getpcpanel.cpp.windows;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioDeviceEvent;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.Role;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.util.ExtractUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@ConditionalOnWindows
@RequiredArgsConstructor
@SuppressWarnings("unused") // Methods are called from JNI
public class SndCtrlWindows implements ISndCtrl {
    private final ExtractUtil extractUtil;
    private final ApplicationEventPublisher eventPublisher;
    @GuardedBy("defaults") private final Map<DefaultFor, String> defaults = new HashMap<>();
    @GuardedBy("devices") private final Map<String, AudioDevice> devices = new HashMap<>();

    @PostConstruct
    public void init() {
        loadLibrary();
        SndCtrlNative.start(this);
    }

    private void loadLibrary() {
        try {
            System.loadLibrary("SndCtrl");
            log.warn("Debugging? Loading SndCtrl from the path.");
        } catch (Throwable e) {
            try {
                System.load(extractUtil.extractAndDeleteOnExit("SndCtrl.dll").toString());
            } catch (Throwable ex) {
                log.error("Unable to load sndctrl, volume options will be disabled", ex);
            }
        }
    }

    private void loadLinux() {
        try {
            System.load(extractUtil.extractAndDeleteOnExit("libsndctrl.so").toString());
        } catch (Throwable ex) {
            log.error("Unable to load sndctrl, volume options will be disabled", ex);
        }
    }

    @Override
    public Collection<AudioDevice> getDevices() {
        synchronized (devices) {
            return Collections.unmodifiableCollection(devices.values());
        }
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).flatCollection(ad -> ad.getSessions().values()).distinct(AudioSession::pid).toSet();
        }
    }

    @Override
    public AudioDevice getDevice(String id) {
        synchronized (devices) {
            return devices.get(id);
        }
    }

    @Override
    public void setDeviceVolume(String deviceId, float volume) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        log.trace("Set device volume to {} for {}", volume, deviceOrDefault);
        SndCtrlNative.instance.setDeviceVolume(deviceOrDefault, volume);
    }

    @Override
    public void muteDevice(String deviceId, MuteType mute) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        AudioDevice device;
        synchronized (devices) {
            device = devices.get(deviceOrDefault);
        }
        if (device == null) {
            log.warn("No device found for {}", deviceOrDefault);
            return;
        }

        log.trace("Mute device {}", deviceOrDefault);
        SndCtrlNative.instance.muteDevice(deviceOrDefault, mute.convert(device.muted()));
    }

    @Override
    public void setDefaultDevice(String deviceId) {
        log.trace("Set default device to {}", deviceId);
        SndCtrlNative.instance.setDefaultDevice(deviceId, DataFlow.dfAll.ordinal(), Role.roleMultimedia.ordinal());
    }

    public void setDefaultDevice(String deviceName, DataFlow flow, Role role) {
        if (StringUtils.isBlank(deviceName)) {
            return;
        }
        synchronized (devices) {
            StreamEx.ofValues(devices).findFirst(d -> d.dataflow() == flow && StringUtils.containsIgnoreCase(d.name(), deviceName)).ifPresent(d -> SndCtrlNative.instance.setDefaultDevice(d.id(), flow.ordinal(), role.ordinal()));
        }
    }

    @Override
    public void setProcessVolume(String fileName, String device, float volume) {
        synchronized (devices) {
            var deviceId = defaultDeviceOnEmpty(device);
            StreamEx.ofValues(devices)
                    .filter(d -> "*".equals(device) || deviceId.equals(d.id()))
                    .flatCollection(d -> d.getSessions().values())
                    .filter(s -> (fileName.equals(AudioSession.SYSTEM) && s.pid() == 0) || (s.executable() != null && StringUtils.equals(fileName, s.executable().getName())))
                    .forEach(s -> setProcessVolume(s, volume));
        }
    }

    public void setProcessVolume(AudioSession session, float volume) {
        log.trace("Setting volume to {} for {}", volume, session);
        SndCtrlNative.instance.setProcessVolume(session.device().id(), session.pid(), volume);
    }

    @Override
    public void setFocusVolume(float volume) {
        SndCtrlNative.instance.setFocusVolume(volume);
    }

    @Override
    public void muteProcesses(Set<String> fileName, MuteType mute) {
        synchronized (devices) {
            StreamEx.ofValues(devices).flatCollection(d -> d.getSessions().values())
                    .filter(s -> s.executable() != null && fileName.contains(s.executable().getName()))
                    .forEach(s -> muteProcess(s, mute));
        }
    }

    public void muteProcess(AudioSession session, MuteType muted) {
        log.trace("Muting session {}", session);
        SndCtrlNative.instance.muteSession(session.device().id(), session.pid(), muted.convert(session.muted()));
    }

    @Override
    public String getFocusApplication() {
        return SndCtrlNative.instance.getFocusApplication();
    }

    @Override
    public List<File> getRunningApplications() {
        var running = new HashSet<String>();
        var arr = SndCtrlNative.instance.getAllRunningProcesses();
        return StreamEx.of(arr).map(StringUtils::trimToNull).nonNull().map(File::new).sorted(Comparator.comparing(File::getName)).toImmutableList();
    }

    @Override
    public String defaultDeviceOnEmpty(String deviceId) {
        if (StringUtils.isNotBlank(deviceId) && !StringUtils.equals("default", deviceId)) {
            return deviceId;
        }
        return defaultPlayer();
    }

    @Override
    public String defaultPlayer() {
        synchronized (defaults) {
            return defaults.get(new DefaultFor(DataFlow.dfRender.ordinal(), Role.roleMultimedia.ordinal()));
        }
    }

    private AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new WindowsAudioDevice(eventPublisher, name, id).volume(volume).muted(muted).dataflow(DataFlow.from(dataFlow));
        synchronized (devices) {
            devices.put(id, result);
        }
        log.trace("Device added: {}", result);

        eventPublisher.publishEvent(new AudioDeviceEvent(result, EventType.ADDED));
        return result;
    }

    private void deviceRemoved(String id) {
        log.trace("Device removed: {}", id);
        AudioDevice removed;
        synchronized (devices) {
            removed = devices.remove(id);
        }
        if (removed != null) {
            eventPublisher.publishEvent(new AudioDeviceEvent(removed, EventType.REMOVED));
        }
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        synchronized (defaults) {
            defaults.put(new DefaultFor(dataFlow, role), id);
        }
        log.trace("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    private void focusChanged(String to) {
        log.trace("Focus changed to {}", to);
        eventPublisher.publishEvent(new WindowFocusChangedEvent(to));
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
