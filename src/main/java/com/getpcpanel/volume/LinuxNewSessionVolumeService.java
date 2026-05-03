package com.getpcpanel.volume;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.windows.WindowsAudioSession;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@LinuxBuild
public class LinuxNewSessionVolumeService {
    private final FocusVolumeListener focusVolumeListener = this::onFocusVolumeChange; // Ensures single reference for removing
    @Inject DeviceHolder devices;
    @Inject SaveService save;
    @Inject ISndCtrl sndCtrl;
    @Inject VolumeCoordinatorService volumeCoordinatorService;

    private Map<FocusVolumeEvent.FocusVolumeTarget, Float> storedFocusAppVolume = new HashMap<>();

    @PostConstruct
    public void init() {
        volumeCoordinatorService.addFocusVolumeListener(focusVolumeListener);
    }

    @PreDestroy
    public void destroy() {
        volumeCoordinatorService.removeFocusVolumeListener(focusVolumeListener);
    }

    private void onFocusVolumeChange(FocusVolumeEvent focusVolumeEvent) {
        storedFocusAppVolume.put(focusVolumeEvent.target(), focusVolumeEvent.volume());
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

        if (triggerStoredFocusAppVolume(exe)) {
            return true;
        }

        return false;
    }

    private boolean triggerStoredFocusAppVolume(String exe) {
        var stored = storedFocusAppVolume.get(new FocusVolumeEvent.FocusVolumeTarget(FocusVolumeEventType.process, exe));
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

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given command matches the session's executable and (on Windows)
     * the session's audio device.
     */
    private boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (session.executable() == null)
            return false;
        if (!c.getProcessName().contains(session.executable().getName())) {
            return false;
        }
        var deviceId = c.getDevice();
        if (StringUtils.isBlank(deviceId) || "*".equals(deviceId))
            return true;
        if (session instanceof WindowsAudioSession was) {
            return deviceId.equals(was.device().id());
        }
        return false;
    }


    /**
     * Returns {@code true} if the WaveLink command targets the channel (Input or Channel type)
     * with the given channel ID.
     */
    private boolean isWaveLinkFocusChannel(CommandWaveLinkChangeLevel c, String channelId) {
        var type = c.getCommandType();
        return (type == WaveLinkCommandTarget.Input || type == WaveLinkCommandTarget.Channel)
                && channelId.equals(c.getId1());
    }
}
