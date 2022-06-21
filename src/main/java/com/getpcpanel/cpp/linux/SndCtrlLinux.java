package com.getpcpanel.cpp.linux;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.ConditionalOnLinux;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@ConditionalOnLinux
@RequiredArgsConstructor
public class SndCtrlLinux implements ISndCtrl {
    private final PulseAudioWrapper cmd;
    private final ProcessHelper processHelper;
    private final ApplicationEventPublisher eventPublisher;
    @GuardedBy("devices") private final Map<String, LinuxAudioDevice> devices = new HashMap<>();
    @GuardedBy("sessions") private final Map<Integer, LinuxAudioSession> sessions = new HashMap<>();

    @PostConstruct
    public void init() {
        initDevices();
        initSessions();
    }

    @EventListener(PulseAudioEventListener.LinuxDeviceChangedEvent.class)
    public void initDevices() {
        synchronized (devices) {
            devices.clear();
            StreamEx.of(getDevicesFromCmd()).mapToEntry(AudioDevice::id, Function.identity()).into(devices);
        }
    }

    @EventListener(PulseAudioEventListener.LinuxSessionChangedEvent.class)
    public void initSessions() {
        synchronized (sessions) {
            sessions.clear();
            StreamEx.of(getSessionsFromCmd()).mapToEntry(AudioSession::pid, Function.identity()).into(sessions);
        }
    }

    @Override
    public Map<String, AudioDevice> getDevicesMap() {
        synchronized (devices) {
            return new HashMap<>(devices);
        }
    }

    @Override
    public Collection<AudioDevice> getDevices() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).select(AudioDevice.class).toSet();
        }
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        synchronized (sessions) {
            return StreamEx.ofValues(sessions).select(AudioSession.class).toSet();
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
        cmd.setDeviceVolume(deviceIdx(deviceId), volume);
    }

    @Override
    public void muteDevice(String deviceId, MuteType mute) {
        cmd.muteDevice(deviceIdx(deviceId), mute);
    }

    @Override
    public void setDefaultDevice(String deviceId) {
        LinuxAudioDevice device;
        synchronized (devices) {
            device = devices.get(deviceId);
        }
        if (device == null)
            return;
        cmd.setDefaultDevice(device.index());
    }

    @Override
    public void setProcessVolume(String fileName, String device, float volume) {
        Set<LinuxAudioSession> todo;
        synchronized (sessions) {
            todo = StreamEx.ofValues(sessions)
                           .filter(s -> StringUtils.equals(fileName, s.executable().getName()))
                           .toSet();
        }
        todo.forEach(s -> cmd.setSessionVolume(s.index(), volume));
    }

    @Override
    public void setFocusVolume(float volume) {
        var process = processHelper.getActiveProcess();
        if (process == null) {
            return;
        }
        setProcessVolume(process, null, volume);
    }

    @Override
    public void muteProcesses(Set<String> fileName, MuteType mute) {
        Set<LinuxAudioSession> todo;
        synchronized (sessions) {
            todo = StreamEx.ofValues(sessions)
                           .filter(s -> fileName.contains(s.executable().getName()))
                           .toSet();
        }
        todo.forEach(s -> cmd.muteSession(s.index(), mute));
    }

    @Override
    public String getFocusApplication() {
        return null;
    }

    @Override
    public List<RunningApplication> getRunningApplications() {
        synchronized (sessions) {
            return StreamEx.ofValues(sessions).map(AudioSession::executable).map(f -> new RunningApplication(0, f)).toList();
        }
    }

    @Override
    public String defaultDeviceOnEmpty(String deviceId) {
        return null;
    }

    @Override
    public String defaultPlayer() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).findFirst(LinuxAudioDevice::isDefault).map(AudioDevice::id).orElse(null);
        }
    }

    @Override
    public String defaultRecorder() {
        return null;
    }

    private Set<LinuxAudioDevice> getDevicesFromCmd() {
        return StreamEx.of(cmd.getDevices()).map(pa -> new LinuxAudioDevice(eventPublisher, pa.index(), pa.properties().get("device.product.name"), pa.metas().get("name"), pa.isDefault())).toSet();
    }

    private Set<LinuxAudioSession> getSessionsFromCmd() {
        return StreamEx.of(cmd.getSessions())
                       .filter(pa -> StringUtils.isNotBlank(pa.properties().get("application.process.id")))
                       .map(pa ->
                               new LinuxAudioSession(null, eventPublisher,
                                       pa.index(),
                                       NumberUtils.toInt(pa.properties().get("application.process.id"), -1),
                                       new File(pa.properties().get("application.process.binary")),
                                       pa.properties().get("application.name"),
                                       "", 0, false))
                       .distinct(AudioSession::pid)
                       .toSet();
    }

    private int deviceIdx(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return PulseAudioWrapper.DEFAULT_DEVICE;
        }
        synchronized (devices) {
            return Optional.ofNullable(devices.get(deviceId)).map(LinuxAudioDevice::index).orElse(PulseAudioWrapper.NO_OP_IDX);
        }
    }
}
