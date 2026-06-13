package com.getpcpanel.volume;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@LinuxBuild
@ApplicationScoped
public class LinuxNewSessionVolumeService implements IFocusRedirector {
    @Inject DeviceHolder devices;
    @Inject SaveService save;
    @Inject ISndCtrl sndCtrl;

    private final Map<String, Float> storedFocusAppVolume = new HashMap<>();

    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        if (targetProcess != null) {
            storedFocusAppVolume.put(StringUtils.lowerCase(targetProcess), volume);
        }
        return false;
    }

    public boolean onNewAudioSession(@Observes AudioSessionEvent event) {
        if (event.eventType() != EventType.ADDED && !(save.get().isForceVolume() && event.eventType() == EventType.CHANGED)) {
            return false;
        }

        var session = event.session();
        if (session.executable() == null) {
            return false;
        }

        var exe = session.executable().getName();
        if (triggerCommandVolumeProcessIfAvailable(event, exe)) {
            return true;
        }

        return triggerStoredFocusAppVolume(exe);
    }

    private boolean triggerStoredFocusAppVolume(String exe) {
        var stored = storedFocusAppVolume.get(StringUtils.lowerCase(exe));
        if (stored != null) {
            sndCtrl.setProcessVolume(exe, null, stored);
            return true;
        }
        return false;
    }

    private boolean triggerCommandVolumeProcessIfAvailable(AudioSessionEvent event, String exe) {
        if (devices.hasCommandsOf(CommandVolumeProcess.class, c -> isProcessAndDevice(event, c))) {
            log.debug("New session [{}]: applying direct process control", exe);
            devices.triggerCommandsOf(CommandVolumeProcess.class,
                    s -> s.filterValues(c -> isProcessAndDevice(event, c)));
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the given command matches the session's executable
     */
    private boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (session.executable() == null)
            return false;
        if (!c.getProcessName().contains(session.executable().getName())) {
            return false;
        }
        var deviceId = c.getDevice();
        return StringUtils.isBlank(deviceId) || "*".equals(deviceId);
    }
}
