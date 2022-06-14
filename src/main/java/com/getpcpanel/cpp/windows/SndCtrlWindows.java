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
@SuppressWarnings("ALL") // Methods are called from JNI
@RequiredArgsConstructor
public class SndCtrlWindows implements ISndCtrl {
    private final ExtractUtil extractUtil;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        loadLibrary();
        SndCtrlNative.instance.start(this);
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

    private Map<DefaultFor, String> defaults = new HashMap<>();
    private Map<String, AudioDevice> devices = new HashMap<>();

    public Collection<AudioDevice> getDevices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        return StreamEx.of(getDevices()).flatCollection(ad -> ad.getSessions().values()).distinct(AudioSession::pid).toSet();
    }

    public AudioDevice getDevice(String id) {
        return devices.get(id);
    }

    public void setDeviceVolume(String deviceId, float volume) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        log.trace("Set device volume to {} for {}", volume, deviceOrDefault);
        SndCtrlNative.instance.setDeviceVolume(deviceOrDefault, volume);
    }

    public void muteDevice(String deviceId, MuteType mute) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        var device = devices.get(deviceOrDefault);
        if (device == null) {
            log.warn("No device found for {}", deviceOrDefault);
            return;
        }

        log.trace("Mute device {}", deviceOrDefault);
        SndCtrlNative.instance.muteDevice(deviceOrDefault, mute.convert(device.muted()));
    }

    public void setDefaultDevice(String deviceId) {
        log.trace("Set default device to {}", deviceId);
        SndCtrlNative.instance.setDefaultDevice(deviceId, DataFlow.dfAll.ordinal(), Role.roleMultimedia.ordinal());
    }

    public void setProcessVolume(String fileName, String device, float volume) {
        var deviceId = defaultDeviceOnEmpty(device);
        StreamEx.ofValues(devices)
                .filter(d -> device.equals("*") || deviceId.equals(d.id()))
                .flatCollection(d -> d.getSessions().values())
                .filter(s -> (fileName.equals(AudioSession.SYSTEM) && s.pid() == 0) || (s.executable() != null && StringUtils.equals(fileName, s.executable().getName())))
                .forEach(s -> setProcessVolume(s, volume));
    }

    public void setProcessVolume(AudioSession session, float volume) {
        log.trace("Setting volume to {} for {}", volume, session);
        SndCtrlNative.instance.setProcessVolume(session.device().id(), session.pid(), volume);
    }

    public void setFocusVolume(float volume) {
        SndCtrlNative.instance.setFocusVolume(volume);
    }

    public void muteProcesses(Set<String> fileName, MuteType mute) {
        StreamEx.ofValues(devices).flatCollection(d -> d.getSessions().values())
                .filter(s -> s.executable() != null && fileName.contains(s.executable().getName()))
                .forEach(s -> muteProcess(s, mute));
    }

    public void muteProcess(AudioSession session, MuteType muted) {
        log.trace("Muting session {}", session);
        SndCtrlNative.instance.muteSession(session.device().id(), session.pid(), muted.convert(session.muted()));
    }

    public String getFocusApplication() {
        return SndCtrlNative.instance.getFocusApplication();
    }

    public List<File> getRunningApplications() {
        var running = new HashSet<String>();
        var arr = SndCtrlNative.instance.getAllRunningProcesses();
        return StreamEx.of(arr).map(StringUtils::trimToNull).nonNull().map(File::new).sorted(Comparator.comparing(File::getName)).toImmutableList();
    }

    public String defaultDeviceOnEmpty(String deviceId) {
        if (StringUtils.isNotBlank(deviceId) && !StringUtils.equals("default", deviceId)) {
            return deviceId;
        }
        return defaultPlayer();
    }

    public String defaultPlayer() {
        return defaults.get(new DefaultFor(DataFlow.dfRender.ordinal(), Role.roleMultimedia.ordinal()));
    }

    private AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new WindowsAudioDevice(eventPublisher, name, id).volume(volume).muted(muted).dataflow(DataFlow.from(dataFlow));
        devices.put(id, result);
        log.trace("Device added: {}", result);

        eventPublisher.publishEvent(new AudioDeviceEvent(result, EventType.ADDED));
        return result;
    }

    private void deviceRemoved(String id) {
        log.trace("Device removed: {}", id);
        var removed = devices.remove(id);
        if (removed != null) {
            eventPublisher.publishEvent(new AudioDeviceEvent(removed, EventType.REMOVED));
        }
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        defaults.put(new DefaultFor(dataFlow, role), id);
        log.trace("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    private void focusChanged(String to) {
        log.trace("Focus changed to {}", to);
        eventPublisher.publishEvent(new WindowFocusChangedEvent(to));
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
