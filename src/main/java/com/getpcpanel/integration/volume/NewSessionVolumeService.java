package com.getpcpanel.integration.volume;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.AudioSessionEvent;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Gives an app that starts playing sound the level of the control that names it: an App-volume binding, or the
 * focus dial's last level for that app. On by default on Linux, where a new stream starts at the sink's default;
 * off by default on Windows, which restores each app's own volume. Apps in the exception list get the opposite.
 */
@Log4j2
@ApplicationScoped
class NewSessionVolumeService implements IFocusRedirector {
    /** Below a step of the hardware's 0-255 range: a volume read back after setting it can differ by rounding. */
    private static final float VOLUME_EPSILON = 0.002f;

    @Inject DeviceHolder devices;
    @Inject SaveService save;
    @Inject ISndCtrl sndCtrl;

    private final Map<String, Float> storedFocusAppVolume = new HashMap<>();
    /** The volume each session last had, so force volume reacts to a changed volume only. */
    private final Map<String, Float> lastVolumes = new ConcurrentHashMap<>();

    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        if (targetProcess != null) {
            storedFocusAppVolume.put(StringUtils.lowerCase(targetProcess), volume);
        }
        return false;
    }

    public boolean onNewAudioSession(@Observes AudioSessionEvent event) {
        if (event.eventType() == EventType.REMOVED) {
            lastVolumes.remove(key(event.session()));
            return false;
        }
        var volumeChanged = volumeChanged(event.session());
        // Force volume: a session that changed its volume gets its control's level back. Only the volume counts:
        // Windows also reports title and icon changes, and echoes PCPanel's own change of the volume, which would
        // otherwise be re-applied forever.
        var forced = event.eventType() == EventType.CHANGED && save.get().isForceVolume() && volumeChanged;
        if (event.eventType() != EventType.ADDED && !forced) {
            return false;
        }

        var session = event.session();
        // Force volume is its own switch: it does not depend on "new apps start at their control's level".
        if (session.executable() == null || !forced && !applies(session)) {
            return false;
        }

        var exe = session.executable().getName();
        var applied = triggerCommandVolumeProcessIfAvailable(event, exe) || triggerStoredFocusAppVolume(session);
        if (applied && forced) {
            // Linux does not report PCPanel's own write back, so the volume recorded here would stay at the one the
            // app was moved to, and moving it to that same volume again would not count as a change. Forget it: the
            // next volume seen is a change (on Windows, which does report it, that is one more write of the same level).
            lastVolumes.remove(key(session));
        }
        return applied;
    }

    /** Records the session's volume; returns whether it differs from the last one seen (or is the first). */
    boolean volumeChanged(AudioSession session) {
        var previous = lastVolumes.put(key(session), session.volume());
        return previous == null || Math.abs(previous - session.volume()) > VOLUME_EPSILON;
    }

    private static String key(AudioSession session) {
        return session.pid() + "|" + session.executable();
    }

    /** Whether this session takes its control's level: the setting, inverted for the apps listed as exceptions. */
    boolean applies(AudioSession session) {
        var s = save.get();
        var excepted = StreamEx.of(s.getNewAppsAtDialLevelExceptions()).anyMatch(session::matches);
        return s.effectiveNewAppsAtDialLevel() != excepted;
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
            // As a sync, not a turn of the control: "no volume jumps" must not hold back the level being restored or
            // forced, and no overlay pops up for it.
            devices.triggerCommandsOf(CommandVolumeProcess.class,
                    s -> s.filterValues(c -> isProcessAndDevice(event, c)), true);
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
        // Blank is every output device; a chosen one only its own sessions (where the platform knows a session's device).
        var deviceId = c.getDevice();
        return StringUtils.isBlank(deviceId) || "*".equals(deviceId) || session.deviceId() == null || deviceId.equals(session.deviceId());
    }
}
