package com.getpcpanel.cpp;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

    public static void setProcessVolume(String fileName, float volume) {
        StreamEx.ofValues(instance.devices).flatCollection(d -> d.getSessions().values())
                .filter(s -> s.executable() != null && StringUtils.equals(fileName, s.executable().getName()))
                .forEach(s -> setProcessVolume(s, volume));
    }

    public static void setProcessVolume(AudioSession session, float volume) {
        log.trace("Setting volume to {} for {}", volume, session);
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
        log.trace("Device added: {}", result);

        return result;
    }

    private void deviceRemoved(String id) {
        devices.remove(id);
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        defaults.put(new DefaultFor(dataFlow, role), id);
        log.trace("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
