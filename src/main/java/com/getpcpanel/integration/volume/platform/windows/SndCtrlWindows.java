package com.getpcpanel.integration.volume.platform.windows;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioDeviceEvent;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.Role;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.profile.WindowFocusChangedEvent;
import com.getpcpanel.util.io.ExtractUtil;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@WindowsBuild
@SuppressWarnings("unused") // Methods are called from JNI
public class SndCtrlWindows implements ISndCtrl {
    @Inject ExtractUtil extractUtil;
    @Inject Event<Object> eventBus;
    @GuardedBy("defaults") private final Map<DefaultFor, String> defaults = new HashMap<>();
    @GuardedBy("devices") private final Map<String, WindowsAudioDevice> devices = new HashMap<>();
    /**
     * The bus every event raised from a JNI callback goes through. The DLL makes those calls holding
     * its global audio lock, so observers must not run on that thread — see {@link CallbackEventBus}.
     */
    @Nullable private CallbackEventBus callbackEvents;

    @PostConstruct
    public void init() {
        callbackEvents = new CallbackEventBus(eventBus);
        loadLibrary();
        SndCtrlNative.start(this);

        if (SndCtrlNative.instance.hasAudioPolicyConfigFactory()) {
            log.info("AudioPolicyConfigFactory is available");
        } else {
            log.warn("AudioPolicyConfigFactory is not available");
        }
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

    @Override
    public Map<String, AudioDevice> getDevicesMap() {
        synchronized (devices) {
            return new HashMap<>(devices);
        }
    }

    @Override
    public Collection<AudioDevice> devices() {
        synchronized (devices) {
            return Collections.unmodifiableCollection(devices.values());
        }
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).flatCollection(ad -> ad.getSessions().values()).distinct(AudioSession::pid).select(AudioSession.class).toSet();
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
        String deviceId;
        synchronized (devices) {
            deviceId = StreamEx.ofValues(devices).findFirst(d -> d.dataflow() == flow && StringUtils.containsIgnoreCase(d.name(), deviceName)).map(AudioDevice::id).orElse(null);
        }
        if (deviceId != null) {
            applyDefaultDevice(deviceId, flow, role);
        }
    }

    /** The DLL call behind {@link #setDefaultDevice(String, DataFlow, Role)}, as its own seam. */
    void applyDefaultDevice(String deviceId, DataFlow flow, Role role) {
        SndCtrlNative.instance.setDefaultDevice(deviceId, flow.ordinal(), role.ordinal());
    }

    /**
     * Runs {@code action} for each resolved target with the {@link #devices} monitor <b>released</b>.
     *
     * <p>Every action here enters {@code SndCtrl.dll}, and the DLL holds its own {@code g_audioMutex}
     * while calling back up into {@link #deviceAdded} / {@link #deviceRemoved} — both of which take that
     * monitor. Entering the DLL while holding it therefore forms an AB-BA cycle with the DLL's
     * notification thread and both park forever. The damage is not confined to audio: every dial and
     * button is executed by the single command-dispatch thread, so one such hang silently kills all
     * hardware control until the application is restarted. Resolve targets under the lock; call out
     * from outside it.
     */
    private static <T> void callNativeForEach(List<T> targets, Consumer<T> action) {
        targets.forEach(action);
    }

    @Override
    public void setProcessVolume(String fileName, String device, float volume) {
        var deviceId = defaultDeviceOnEmpty(device);
        List<WindowsAudioSession> targets;
        synchronized (devices) {
            targets = StreamEx.ofValues(devices)
                    // deviceId is null until the first default-device callback arrives, and stays null
                    // while the machine has no default playback device; no device matches either way.
                    .filter(d -> ("*".equals(device) && d.dataflow() == DataFlow.dfRender) || StringUtils.equals(deviceId, d.id()))
                    .flatCollection(d -> d.getSessions().values())
                    .filter(s -> (StringUtils.equalsIgnoreCase(fileName, AudioSession.SYSTEM) && s.isSystemSounds()) || (s.executable() != null && StringUtils.equalsIgnoreCase(fileName, s.executable().getName())))
                    .toList();
        }
        callNativeForEach(targets, s -> setProcessVolume(s, volume));
    }

    public void setProcessVolume(WindowsAudioSession session, float volume) {
        log.trace("Setting volume to {} for {}", volume, session);
        SndCtrlNative.instance.setProcessVolume(session.device().id(), session.pid(), volume);
    }

    @Override
    public void setFocusVolume(float volume) {
        SndCtrlNative.instance.setFocusVolume(volume);
    }

    @Override
    public void muteProcesses(Set<String> fileName, MuteType mute) {
        var lcFileNames = StreamEx.of(fileName).map(String::toLowerCase).toImmutableSet();
        var systemSounds = lcFileNames.contains(AudioSession.SYSTEM.toLowerCase());
        List<WindowsAudioSession> targets;
        synchronized (devices) {
            targets = StreamEx.ofValues(devices).flatCollection(d -> d.getSessions().values())
                    .filter(s -> (systemSounds && s.isSystemSounds())
                            || (s.executable() != null && (lcFileNames.contains(s.executable().getName().toLowerCase()) || lcFileNames.contains(s.executable().getAbsolutePath().toLowerCase()))))
                    .toList();
        }
        callNativeForEach(targets, s -> muteProcess(s, mute));
    }

    public void muteProcess(WindowsAudioSession session, MuteType muted) {
        log.trace("Muting session {}", session);
        SndCtrlNative.instance.muteSession(session.device().id(), session.pid(), muted.convert(session.muted()));
    }

    @Override
    public String getFocusApplication() {
        return SndCtrlNative.instance.getFocusApplication();
    }

    @Override
    public List<RunningApplication> getRunningApplications() {
        return ProcessHelper.getRunningApplications();
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
            return defaults.get(DefaultFor.mediaPlayback);
        }
    }

    @Override
    public String defaultRecorder() {
        synchronized (defaults) {
            return defaults.get(DefaultFor.mediaRecord);
        }
    }

    public AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new WindowsAudioDevice(eventBus, callbackEvents, name, id).volume(volume).muted(muted).dataflow(DataFlow.from(dataFlow));
        synchronized (devices) {
            devices.put(id, result);
        }
        log.trace("Device added: {}", result);

        fireEvent(new AudioDeviceEvent(result, EventType.ADDED));
        return result;
    }

    private void fireEvent(Object result) {
        CallbackEventBus.fire(callbackEvents, result);
    }

    public void deviceRemoved(String id) {
        log.trace("Device removed: {}", id);
        AudioDevice removed;
        synchronized (devices) {
            removed = devices.remove(id);
        }
        if (removed != null) {
            fireEvent(new AudioDeviceEvent(removed, EventType.REMOVED));
        }
    }

    public void setDefaultDevice(String id, int dataFlow, int role) {
        synchronized (defaults) {
            defaults.put(DefaultFor.of(dataFlow, role), id);
        }
        log.trace("Default changed: {}: {}", DefaultFor.of(dataFlow, role), id);
    }

    public void focusChanged(String to) {
        log.trace("Focus changed to {}", to);
        fireEvent(new WindowFocusChangedEvent(to));
    }

    public Map<DefaultFor, String> getDefaults() {
        synchronized (defaults) {
            return new HashMap<>(defaults);
        }
    }

    public boolean setDeviceForProcess(int pid, @Nonnull DataFlow flow, @Nullable String deviceId) {
        return SndCtrlNative.instance.setPersistedDefaultAudioEndpoint(pid, flow.ordinal(), deviceId);
    }

    public Set<Integer> getPidsFor(String process) {
        synchronized (devices) {
            return StreamEx.ofValues(devices).flatCollection(d -> d.getSessions().values())
                           .filter(s -> s.executable() != null && StringUtils.containsIgnoreCase(s.executable().getAbsolutePath(), process))
                           .map(AudioSession::pid)
                           .toImmutableSet();
        }
    }

    public void triggerAv() {
        SndCtrlNative.instance.triggerAv();
    }

    @RequiredArgsConstructor
    public enum DefaultFor {
        mediaPlayback(DataFlow.dfRender.ordinal(), Role.roleMultimedia.ordinal()),
        mediaRecord(DataFlow.dfCapture.ordinal(), Role.roleMultimedia.ordinal()),
        communicationPlayback(DataFlow.dfRender.ordinal(), Role.roleCommunications.ordinal()),
        communicationRecord(DataFlow.dfCapture.ordinal(), Role.roleCommunications.ordinal());

        @Nullable
        public static DefaultFor of(DataFlow dataFlow, Role role) {
            return of(dataFlow.ordinal(), role.ordinal());
        }

        @Nullable
        public static DefaultFor of(int dataFlow, int role) {
            return StreamEx.of(values()).findFirst(d -> d.dataFlow == dataFlow && d.role == role).orElse(null);
        }

        private final int dataFlow;
        private final int role;
    }
}
