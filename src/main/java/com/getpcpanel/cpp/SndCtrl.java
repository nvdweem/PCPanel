package com.getpcpanel.cpp;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.util.Util;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@SuppressWarnings("ALL") // Methods are called from JNI
public enum SndCtrl {
    instance;

    static {
        try {
            System.loadLibrary("SndCtrl");
            log.warn("Debugging? Loading SndCtrl from the path.");
        } catch (Throwable e) {
            System.load(Util.extractAndDeleteOnExit("SndCtrl.dll").toString());
        }
        SndCtrlNative.instance.start(instance);
    }

    private Map<DefaultFor, String> defaults = new HashMap<>();
    private Map<String, AudioDevice> devices = new HashMap<>();

    public static Collection<AudioDevice> getDevices() {
        return Collections.unmodifiableCollection(instance.devices.values());
    }

    public static AudioDevice getDevice(String id) {
        return instance.devices.get(id);
    }

    public static void setDeviceVolume(String deviceId, float volume) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        log.trace("Set device volume to {} for {}", volume, deviceOrDefault);
        SndCtrlNative.instance.setDeviceVolume(deviceOrDefault, volume);
    }

    public static void muteDevice(String deviceId, MuteType mute) {
        var deviceOrDefault = defaultDeviceOnEmpty(deviceId);
        var device = instance.devices.get(deviceOrDefault);
        if (device == null) {
            log.warn("No device found for {}", deviceOrDefault);
            return;
        }

        log.trace("Mute device {}", deviceOrDefault);
        SndCtrlNative.instance.muteDevice(deviceOrDefault, mute.convert(device.muted()));
    }

    public static void setDefaultDevice(String deviceId) {
        log.trace("Set default device to {}", deviceId);
        SndCtrlNative.instance.setDefaultDevice(deviceId, DataFlow.dfAll.ordinal(), Role.roleMultimedia.ordinal());
    }

    public static void setProcessVolume(String fileName, String device, float volume) {
        var deviceId = defaultDeviceOnEmpty(device);
        StreamEx.ofValues(instance.devices)
                .filter(d -> device.equals("*") || deviceId.equals(d.id()))
                .flatCollection(d -> d.getSessions().values())
                .filter(s -> (fileName.equals(AudioSession.SYSTEM) && s.pid() == 0) || (s.executable() != null && StringUtils.equals(fileName, s.executable().getName())))
                .forEach(s -> setProcessVolume(s, volume));
    }

    public static void setProcessVolume(AudioSession session, float volume) {
        log.trace("Setting volume to {} for {}", volume, session);
        SndCtrlNative.instance.setProcessVolume(session.device().id(), session.pid(), volume);
    }

    public static void setFocusVolume(float volume) {
        SndCtrlNative.instance.setFocusVolume(volume);
    }

    public static void muteProcess(String fileName, MuteType mute) {
        StreamEx.ofValues(instance.devices).flatCollection(d -> d.getSessions().values())
                .filter(s -> s.executable() != null && StringUtils.equals(fileName, s.executable().getName()))
                .forEach(s -> muteProcess(s, mute));
    }

    public static void muteProcess(AudioSession session, MuteType muted) {
        log.trace("Muting session {}", session);
        SndCtrlNative.instance.muteSession(session.device().id(), session.pid(), muted.convert(session.muted()));
    }

    public static String getFocusApplication() {
        return SndCtrlNative.instance.getFocusApplication();
    }

    public static List<File> getRunningApplications() {
        var running = new HashSet<String>();
        SndCtrlNative.instance.addAllRunningProcesses(running);
        return StreamEx.of(running).map(StringUtils::trimToNull).nonNull().map(File::new).sorted(Comparator.comparing(File::getName)).toImmutableList();
    }

    private static String defaultDeviceOnEmpty(String deviceId) {
        if (StringUtils.isNotBlank(deviceId) && !StringUtils.equals("default", deviceId)) {
            return deviceId;
        }
        return instance.defaults.get(new DefaultFor(DataFlow.dfRender.ordinal(), Role.roleMultimedia.ordinal()));
    }

    private AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new AudioDevice(name, id).volume(volume).muted(muted).dataflow(DataFlow.from(dataFlow));
        devices.put(id, result);
        log.trace("Device added: {}", result);

        return result;
    }

    private void deviceRemoved(String id) {
        log.trace("Device removed: {}", id);
        devices.remove(id);
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        defaults.put(new DefaultFor(dataFlow, role), id);
        log.trace("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    private void focusChanged(String to) {
        log.trace("Focus changed to {}", to);
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
