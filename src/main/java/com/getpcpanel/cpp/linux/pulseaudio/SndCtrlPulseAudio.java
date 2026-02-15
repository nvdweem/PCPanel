package com.getpcpanel.cpp.linux.pulseaudio;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.linux.LinuxProcessHelper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@ConditionalOnPulseAudio
@RequiredArgsConstructor
public class SndCtrlPulseAudio implements ISndCtrl {
    public static final String INPUT_PREFIX = "in_";
    private final PulseAudioWrapper cmd;
    private final LinuxProcessHelper processHelper;
    private final ApplicationEventPublisher eventPublisher;
    @GuardedBy("devices") private final Map<String, PulseAudioAudioDevice> devices = new HashMap<>();
    @GuardedBy("sessions") private final Set<PulseAudioAudioSession> sessions = new HashSet<>();

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
            var prev = new HashSet<>(sessions);
            sessions.clear();
            sessions.addAll(getSessionsFromCmd());

            // Trigger events
            var removed = StreamEx.of(prev).remove(sessions::contains);
            var added = StreamEx.of(sessions).remove(prev::contains);
            added.map(sess -> new AudioSessionEvent(sess, EventType.ADDED))
                 .append(removed.map(sess -> new AudioSessionEvent(sess, EventType.REMOVED)))
                 .forEach(eventPublisher::publishEvent);
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
            return allSessions().select(AudioSession.class).toSet();
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
        cmd.setDeviceVolume(isOutput(deviceId), deviceIdx(deviceId), volume);
    }

    @Override
    public void muteDevice(String deviceId, MuteType mute) {
        cmd.muteDevice(isOutput(deviceId), deviceIdx(deviceId), mute);
    }

    @Override
    public void setDefaultDevice(String deviceId) {
        PulseAudioAudioDevice device;
        synchronized (devices) {
            device = devices.get(deviceId);
        }
        if (device == null)
            return;
        cmd.setDefaultDevice(isOutput(deviceId), device.index());
    }

    @Override
    public void setProcessVolume(String fileName, @Nullable String device, float volume) {
        Set<PulseAudioAudioSession> todo;
        synchronized (sessions) {
            todo = allSessions().filter(s -> StringUtils.equalsAnyIgnoreCase(fileName, s.executable().getName(), s.title()))
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
        var lcFileNames = StreamEx.of(fileName).map(String::toLowerCase).toImmutableSet();
        Set<PulseAudioAudioSession> todo;
        synchronized (sessions) {
            todo = allSessions().filter(s -> lcFileNames.contains(StringUtils.lowerCase(s.executable().getName())) || lcFileNames.contains(StringUtils.lowerCase(s.title())))
                                .toSet();
        }
        todo.forEach(s -> cmd.muteSession(s.index(), mute));
    }

    @Override
    public @Nullable String getFocusApplication() {
        return null;
    }

    @Override
    public List<RunningApplication> getRunningApplications() {
        synchronized (sessions) {
            return allSessions().map(AudioSession::executable).map(f -> new RunningApplication(0, f, f.getName())).toList();
        }
    }

    @Override
    public @Nullable String defaultDeviceOnEmpty(String deviceId) {
        return null;
    }

    @Override
    public @Nullable String defaultPlayer() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).findFirst(PulseAudioAudioDevice::isDefaultOutput).map(AudioDevice::id).orElse(null);
        }
    }

    @Override
    public @Nullable String defaultRecorder() {
        return null;
    }

    private Set<PulseAudioAudioDevice> getDevicesFromCmd() {
        return StreamEx.of(cmd.getDevices()).mapPartial(this::toDevice).toSet();
    }

    private Optional<PulseAudioAudioDevice> toDevice(PulseAudioWrapper.PulseAudioTarget pa) {
        var isOutput = pa.type() == PulseAudioWrapper.InOutput.output;
        var name = pa.metas().get("Name");
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        return Optional.of(new PulseAudioAudioDevice(eventPublisher, pa.index(), pa.metas().get("Description"), (isOutput ? "" : INPUT_PREFIX) + pa.metas().get("Name"), pa.isDefault(), isOutput));
    }

    private Set<PulseAudioAudioSession> getSessionsFromCmd() {
        return StreamEx.of(cmd.getSessions())
                       .map(pa ->
                               new PulseAudioAudioSession(eventPublisher,
                                       pa.index(),
                                       NumberUtils.toInt(pa.properties().get("application.process.id"), -1),
                                       new File(pa.properties().getOrDefault("application.process.binary", "/")),
                                       pa.properties().get("application.name"),
                                       "", 0, false))
                       .toSet();
    }

    private int deviceIdx(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return PulseAudioWrapper.DEFAULT_DEVICE;
        }
        synchronized (devices) {
            return Optional.ofNullable(devices.get(deviceId)).map(PulseAudioAudioDevice::index).orElse(PulseAudioWrapper.NO_OP_IDX);
        }
    }

    private static boolean isOutput(String deviceId) {
        return !StringUtils.startsWith(deviceId, INPUT_PREFIX);
    }

    private StreamEx<PulseAudioAudioSession> allSessions() {
        synchronized (sessions) {
            var sessionsCopy = new HashSet<>(sessions);
            return StreamEx.of(sessionsCopy).distinct();
        }
    }
}
