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

    record RunningApplication(int pid, File file) {
    }
}
