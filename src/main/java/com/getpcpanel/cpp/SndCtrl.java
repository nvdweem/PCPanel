package com.getpcpanel.cpp;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@SuppressWarnings("ALL") // Methods are called from JNI
public enum SndCtrl {
    instance;

    static {
        System.loadLibrary("SndCtrl");
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
        log.debug("Set device volume to {} for {}", volume, deviceOrDefault);
        SndCtrlNative.instance.setDeviceVolume(deviceOrDefault, volume);
    }

    public static void setProcessVolume(String fileName, float volume) {
        StreamEx.ofValues(instance.devices).flatCollection(d -> d.getSessions().values())
                .filter(s -> s.executable() != null && StringUtils.equals(fileName, s.executable().getName()))
                .forEach(s -> setProcessVolume(s, volume));
    }

    public static void setProcessVolume(AudioSession session, float volume) {
        log.debug("Setting volume to {} for {}", volume, session);
        SndCtrlNative.instance.setProcessVolume(session.device().id(), session.pid(), volume);
    }

    public static void setFocusVolume(float volume) {
        SndCtrlNative.instance.setFocusVolume(volume);
    }

    private static String defaultDeviceOnEmpty(String deviceId) {
        if (StringUtils.isNotBlank(deviceId)) {
            return deviceId;
        }
        return instance.defaults.get(new DefaultFor(AudioDevice.dfRender, AudioDevice.roleMultimedia));
    }

    private AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new AudioDevice(name, id).volume(volume).muted(muted).dataflow(dataFlow);
        devices.put(id, result);
        log.debug("{}", result);

        return result;
    }

    private void deviceRemoved(String id) {
        devices.remove(id);
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        defaults.put(new DefaultFor(dataFlow, role), id);
        log.debug("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
