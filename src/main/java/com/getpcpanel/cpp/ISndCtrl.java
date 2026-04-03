package com.getpcpanel.cpp;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ISndCtrl {
    Map<String, AudioDevice> getDevicesMap();

    Collection<AudioDevice> getDevices();

    Collection<AudioSession> getAllSessions();

    AudioDevice getDevice(String id);

    void setDeviceVolume(String deviceId, float volume);

    void muteDevice(String deviceId, MuteType mute);

    void setDefaultDevice(String deviceId);

    void setProcessVolume(String fileName, String device, float volume);

    void setFocusVolume(float volume);

    void muteProcesses(Set<String> fileName, MuteType mute);

    String getFocusApplication();

    List<RunningApplication> getRunningApplications();

    String defaultDeviceOnEmpty(String deviceId);

    String defaultPlayer();

    String defaultRecorder();

    record RunningApplication(int pid, File file, String name) {
    }

    static ISndCtrl noOp() {
        return new ISndCtrl() {
            @Override public Map<String, AudioDevice> getDevicesMap() { return Map.of(); }
            @Override public Collection<AudioDevice> getDevices() { return List.of(); }
            @Override public Collection<AudioSession> getAllSessions() { return List.of(); }
            @Override public AudioDevice getDevice(String id) { return null; }
            @Override public void setDeviceVolume(String deviceId, float volume) {}
            @Override public void muteDevice(String deviceId, MuteType mute) {}
            @Override public void setDefaultDevice(String deviceId) {}
            @Override public void setProcessVolume(String fileName, String device, float volume) {}
            @Override public void setFocusVolume(float volume) {}
            @Override public void muteProcesses(Set<String> fileName, MuteType mute) {}
            @Override public String getFocusApplication() { return null; }
            @Override public List<RunningApplication> getRunningApplications() { return List.of(); }
            @Override public String defaultDeviceOnEmpty(String deviceId) { return deviceId; }
            @Override public String defaultPlayer() { return null; }
            @Override public String defaultRecorder() { return null; }
        };
    }
}
