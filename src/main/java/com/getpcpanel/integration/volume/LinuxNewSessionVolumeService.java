package com.getpcpanel.integration.volume;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.AudioSessionEvent;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxNewSessionVolumeService implements IFocusRedirector {
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

        return triggerStoredFocusAppVolume(session);
    }

    /**
     * Re-applies the volume the focus dial last set for this app. The stored key is whatever identified the focused
     * window ({@code ActiveWindow.primaryIdentifier}: a Flatpak id, process, window class or window name), which is
     * rarely the stream's executable name - so the session is matched against it with {@link AudioSession#matches}
     * rather than by an exact name lookup, and that same key is handed to
     * {@link ISndCtrl#setProcessVolume} so every stream of the app is covered.
     */
    private boolean triggerStoredFocusAppVolume(AudioSession session) {
        return storedFocusTarget(session)
                .map(target -> {
                    sndCtrl.setProcessVolume(target, null, storedFocusAppVolume.get(target));
                    return true;
                })
                .orElse(false);
    }

    /** The stored focus identifier this session answers to, if the focus dial has set a volume for it. */
    Optional<String> storedFocusTarget(AudioSession session) {
        return StreamEx.ofKeys(storedFocusAppVolume).findFirst(session::matches);
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
     * Returns {@code true} if the given command names the session. Uses {@link AudioSession#matches} - the same rule
     * the dial itself uses to pick the streams it controls - so a binding that drives a dial also gets its volume
     * restored.
     */
    boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (session.executable() == null)
            return false;
        if (c.getProcessName().stream().noneMatch(session::matches)) {
            return false;
        }
        var deviceId = c.getDevice();
        return StringUtils.isBlank(deviceId) || "*".equals(deviceId);
    }
}
