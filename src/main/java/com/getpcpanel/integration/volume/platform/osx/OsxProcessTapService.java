package com.getpcpanel.integration.volume.platform.osx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.platform.MacBuild;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Per-process volume and mute on macOS via CoreAudio process taps (macOS 14.4+). Keeps one
 * {@link ProcessTap} per adjusted PID so a volume sticks while the user works in other apps, mirroring
 * the Windows per-session behaviour. A volume at the top of the dial removes the tap entirely, handing
 * the audio path back to the OS (no re-render overhead, no lingering permission use).
 *
 * <p>Requires the one-time "System Audio Recording" consent; when the user declines (or the macOS
 * version predates taps) every call degrades to a logged no-op.
 */
@Log4j2
@ApplicationScoped
@MacBuild
@RequiredArgsConstructor
class OsxProcessTapService {
    /** Gains at/above this remove the tap: full volume is byte-identical to not being tapped at all. */
    private static final float FULL_VOLUME = 0.995f;
    private final CoreAudioWrapper wrapper;
    // Command operations are serialized so application intent and its taps change together.
    private final Map<ProcessIdentity, Entry> taps = new HashMap<>();
    // Presence means mute is desired. Pending attempts must not count as successful toggles.
    private final Map<ProcessIdentity, MuteStatus> applicationMutes = new HashMap<>();

    private enum MuteStatus { PENDING, APPLIED }
    private final AtomicBoolean creationFailureLogged = new AtomicBoolean();

    private record Entry(ProcessTap tap, int outputDeviceId) {
    }

    /** Process taps shipped in macOS 14.4 (Sonoma); anything older has no per-process audio hooks. */
    static boolean isSupported() {
        if (!SystemUtils.IS_OS_MAC) {
            return false;
        }
        var parts = SystemUtils.OS_VERSION.split("\\.");
        var major = NumberUtils.toInt(parts[0]);
        var minor = parts.length > 1 ? NumberUtils.toInt(parts[1]) : 0;
        return major > 14 || major == 14 && minor >= 4;
    }

    public synchronized void setVolume(int pid, float volume) {
        if (!isSupported()) {
            return;
        }
        cleanUpDeadProcesses();
        var identity = ProcessIdentity.find(pid);
        if (identity == null) {
            return;
        }
        var entry = taps.get(identity);
        update(identity, volume, entry != null && entry.tap().muted());
    }

    public synchronized boolean isMuted(int pid) {
        cleanUpDeadProcesses();
        var entry = taps.get(ProcessIdentity.find(pid));
        return entry != null && entry.tap().muted();
    }

    public synchronized boolean setMuted(int pid, boolean muted) {
        if (!isSupported()) {
            return false;
        }
        cleanUpDeadProcesses();
        var identity = ProcessIdentity.find(pid);
        if (identity == null) {
            return !muted;
        }
        var entry = taps.get(identity);
        update(identity, entry == null ? 1 : entry.tap().gain(), muted);
        return isMuted(pid) == muted;
    }

    public synchronized void mute(int pid, MuteType muteType) {
        setMuted(pid, muteType.convert(isMuted(pid)));
    }

    public synchronized void muteApplication(int ownerPid, List<Integer> pids, MuteType mute) {
        cleanUpDeadProcesses();
        var owner = ProcessIdentity.find(ownerPid);
        if (owner == null || !isSupported()) {
            return;
        }
        var status = applicationMutes.get(owner);
        var muted = status == MuteStatus.APPLIED
                || status == null && !pids.isEmpty() && pids.stream().allMatch(this::isMuted);
        var target = mute.convert(muted);
        if (target) {
            applicationMutes.put(owner, MuteStatus.PENDING);
            restoreApplicationMute(ownerPid, pids);
        } else {
            pids.forEach(pid -> setMuted(pid, false));
            applicationMutes.remove(owner);
        }
    }

    /** Retry missing taps on commands, including newly discovered WebKit helpers, without a PID applied-state map. */
    public synchronized void restoreApplicationMute(int ownerPid, List<Integer> pids) {
        cleanUpDeadProcesses();
        var owner = ProcessIdentity.find(ownerPid);
        if (owner == null || !applicationMutes.containsKey(owner)) {
            return;
        }
        var applied = applicationMutes.get(owner) == MuteStatus.APPLIED;
        for (var pid : pids) {
            applied |= isMuted(pid) || setMuted(pid, true);
        }
        if (applied) {
            applicationMutes.put(owner, MuteStatus.APPLIED);
        }
    }

    public synchronized void setApplicationVolume(int ownerPid, List<Integer> pids, float volume) {
        cleanUpDeadProcesses();
        var owner = ProcessIdentity.find(ownerPid);
        if (owner == null || !isSupported()) {
            return;
        }
        var muted = applicationMutes.containsKey(owner);
        for (var pid : pids) {
            var identity = ProcessIdentity.find(pid);
            if (identity != null) {
                var entry = taps.get(identity);
                update(identity, volume, muted || entry != null && entry.tap().muted());
            }
        }
        if (muted && pids.stream().anyMatch(this::isMuted)) {
            applicationMutes.put(owner, MuteStatus.APPLIED);
        }
    }

    @PreDestroy
    synchronized void destroy() {
        taps.values().forEach(entry -> entry.tap().close());
        taps.clear();
        applicationMutes.clear();
    }

    /** Apply the requested state directly when recreating a tap; a failed recreation leaves no live entry. */
    private void update(ProcessIdentity identity, float gain, boolean muted) {
        var entry = taps.get(identity);
        if (entry != null && (entry.outputDeviceId() != wrapper.getDefaultDevice(true) || gain >= FULL_VOLUME && !muted)) {
            entry.tap().close();
            taps.remove(identity);
            entry = null;
        }
        if (entry == null) {
            if (gain < FULL_VOLUME || muted) {
                entry = createEntry(identity.pid(), gain, muted);
                if (entry != null) {
                    // Do not retain a tap if the PID changed lifetime during its creation.
                    if (identity.equals(ProcessIdentity.find(identity.pid()))) {
                        taps.put(identity, entry);
                    } else {
                        entry.tap().close();
                    }
                }
            }
        } else {
            entry.tap().setGain(gain);
            entry.tap().setMuted(muted);
        }
    }

    private Entry createEntry(int pid, float gain, boolean muted) {
        var processObject = wrapper.translatePidToProcessObject(pid);
        if (processObject == 0) {
            log.debug("No CoreAudio process object for pid {}, not tapping", pid);
            return null;
        }
        var outputDeviceId = wrapper.getDefaultDevice(true);
        var outputUid = wrapper.deviceUid(outputDeviceId);
        if (outputUid == null) {
            log.warn("No default output device UID, not tapping pid {}", pid);
            return null;
        }
        try {
            var tap = ProcessTap.create(processObject, outputUid, gain);
            tap.setMuted(muted);
            log.debug("Tapped pid {} (process object {}) on '{}'", pid, processObject, outputUid);
            return new Entry(tap, outputDeviceId);
        } catch (ProcessTap.TapCreationException e) {
            if (creationFailureLogged.compareAndSet(false, true)) {
                log.warn("Unable to create a process tap for pid {}. Per-app volume needs the System Audio Recording permission: "
                        + "System Settings > Privacy & Security > Screen & System Audio Recording > enable PCPanel", pid, e);
            } else {
                log.debug("Unable to create a process tap for pid {}", pid, e);
            }
            return null;
        }
    }

    private void cleanUpDeadProcesses() {
        taps.entrySet().removeIf(entry -> {
            if (entry.getKey().equals(ProcessIdentity.find(entry.getKey().pid()))) {
                return false;
            }
            entry.getValue().tap().close();
            return true;
        });
        applicationMutes.keySet().removeIf(owner -> !Objects.equals(owner, ProcessIdentity.find(owner.pid())));
    }
}
