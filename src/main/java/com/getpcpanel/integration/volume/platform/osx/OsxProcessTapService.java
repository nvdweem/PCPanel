package com.getpcpanel.integration.volume.platform.osx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    // Mute intent per application; an owner is in here exactly while it has an exit notification registered.
    private final Map<ProcessIdentity, MuteStatus> applications = new HashMap<>();

    private enum MuteStatus { UNMUTED, PENDING, APPLIED }
    private final AtomicBoolean creationFailureLogged = new AtomicBoolean();

    private record Entry(ProcessTap tap, int outputDeviceId, ProcessIdentity owner, int processObject) {
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
        update(identity, entry == null ? identity : entry.owner(), volume, entry != null && entry.tap().muted());
    }

    public synchronized boolean isMuted(int pid) {
        cleanUpDeadProcesses();
        var entry = taps.get(ProcessIdentity.find(pid));
        return entry != null && entry.tap().muted();
    }

    public synchronized boolean setMuted(int pid, boolean muted) {
        cleanUpDeadProcesses();
        var identity = ProcessIdentity.find(pid);
        var entry = taps.get(identity);
        return identity == null ? !muted : setMuted(entry == null ? identity : entry.owner(), identity, muted);
    }

    private boolean setMuted(ProcessIdentity owner, ProcessIdentity identity, boolean muted) {
        if (!isSupported()) {
            return false;
        }
        var entry = taps.get(identity);
        update(identity, owner, entry == null ? 1 : entry.tap().gain(), muted);
        return isMuted(identity.pid()) == muted;
    }

    public synchronized void mute(int pid, MuteType muteType) {
        setMuted(pid, muteType.convert(isMuted(pid)));
    }

    public synchronized void muteApplication(int ownerPid, List<Integer> pids, MuteType mute) {
        var owner = ProcessIdentity.find(ownerPid);
        var status = applications.get(owner);
        // Snapshot the live audio state before cleanup releases expired-owner mute. A late exit callback
        // must not turn the user's first "unmute" toggle into a new mute. The GUI PID may have no audio.
        var audioPids = pids.stream().filter(pid -> wrapper.translatePidToProcessObject(pid) != 0).toList();
        var audioEntries = audioPids.stream().map(pid -> taps.get(ProcessIdentity.find(pid))).toList();
        var expiredOwnerMute = audioEntries.stream().anyMatch(entry -> entry != null && entry.tap().muted()
                && !entry.owner().equals(ProcessIdentity.find(entry.owner().pid())));
        // A pending mute of an app that has no audio yet counts as muted, so toggling again cancels it. While the app
        // has audio that could not be muted, it is still heard, so a toggle tries to mute it again.
        var muted = status == MuteStatus.APPLIED || status == MuteStatus.PENDING && audioPids.isEmpty()
                || (status == null || status == MuteStatus.UNMUTED) && (expiredOwnerMute
                    || !audioEntries.isEmpty() && audioEntries.stream().allMatch(entry -> entry != null && entry.tap().muted()));
        cleanUpDeadProcesses();
        if (owner == null || !isSupported() || !owner.equals(ProcessIdentity.find(ownerPid))) {
            return;
        }
        var target = mute.convert(muted);
        log.debug("Application mute: owner={}, operation={}, previousMute={}, target={}, expiredOwnerMute={}, audioPids={}", owner, mute, muted, target, expiredOwnerMute, audioPids);
        if (target) {
            if (!observeOwner(owner)) {
                return;
            }
            applications.put(owner, MuteStatus.PENDING);
            restoreApplicationMute(ownerPid, pids);
        } else {
            for (var pid : pids) {
                var identity = ProcessIdentity.find(pid);
                if (identity != null) {
                    setMuted(owner, identity, false);
                }
            }
            if (applications.containsKey(owner)) {
                applications.put(owner, MuteStatus.UNMUTED);
            }
        }
    }

    /** Mutes the processes of an application with mute intent that are not muted yet, such as a WebKit helper that started playing later. */
    public synchronized void restoreApplicationMute(int ownerPid, List<Integer> pids) {
        cleanUpDeadProcesses();
        var owner = ProcessIdentity.find(ownerPid);
        if (owner == null || applications.getOrDefault(owner, MuteStatus.UNMUTED) == MuteStatus.UNMUTED) {
            return;
        }
        var applied = applications.get(owner) == MuteStatus.APPLIED;
        for (var pid : pids) {
            var identity = ProcessIdentity.find(pid);
            if (identity != null) {
                applied |= setMuted(owner, identity, true);
            }
        }
        if (applied) {
            applications.put(owner, MuteStatus.APPLIED);
        }
    }

    public synchronized void setApplicationVolume(int ownerPid, List<Integer> pids, float volume) {
        cleanUpDeadProcesses();
        var owner = ProcessIdentity.find(ownerPid);
        if (owner == null || !isSupported()) {
            return;
        }
        var muted = applications.getOrDefault(owner, MuteStatus.UNMUTED) != MuteStatus.UNMUTED;
        for (var pid : pids) {
            var identity = ProcessIdentity.find(pid);
            if (identity != null) {
                var entry = taps.get(identity);
                update(identity, owner, volume, muted || entry != null && entry.tap().muted());
            }
        }
        if (muted && pids.stream().anyMatch(this::isMuted)) {
            applications.put(owner, MuteStatus.APPLIED);
        }
    }

    @PreDestroy
    synchronized void destroy() {
        taps.values().forEach(entry -> entry.tap().close());
        taps.clear();
        applications.clear();
    }

    /** Apply the requested state directly when recreating a tap; a failed recreation leaves no live entry. */
    private void update(ProcessIdentity identity, ProcessIdentity owner, float gain, boolean muted) {
        if (!owner.equals(ProcessIdentity.find(owner.pid()))) {
            return;
        }
        var entry = taps.get(identity);
        if (entry != null && !entry.owner().equals(owner) && !observeOwner(owner)) {
            return;
        }
        if (entry != null && (entry.outputDeviceId() != wrapper.getDefaultDevice(true) || gain >= FULL_VOLUME && !muted)) {
            closeEntry(identity, entry, "output change or full unmuted volume");
            taps.remove(identity);
            entry = null;
        }
        if (entry == null) {
            if (gain < FULL_VOLUME || muted) {
                if (!observeOwner(owner)) {
                    return;
                }
                entry = createEntry(identity, owner, gain, muted);
                if (entry != null) {
                    // Do not retain a tap if the PID changed lifetime during its creation.
                    if (identity.equals(ProcessIdentity.find(identity.pid())) && owner.equals(ProcessIdentity.find(owner.pid()))) {
                        taps.put(identity, entry);
                    } else {
                        closeEntry(identity, entry, "process or owner changed during creation");
                    }
                }
            }
        } else {
            entry.tap().setGain(gain);
            entry.tap().setMuted(muted);
            if (!entry.owner().equals(owner)) {
                taps.put(identity, new Entry(entry.tap(), entry.outputDeviceId(), owner, entry.processObject()));
            }
        }
    }

    private Entry createEntry(ProcessIdentity identity, ProcessIdentity owner, float gain, boolean muted) {
        var pid = identity.pid();
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
            log.debug("Tapped process={} owner={} processObject={} output='{}' gain={} muted={}", identity, owner, processObject, outputUid, gain, muted);
            return new Entry(tap, outputDeviceId, owner, processObject);
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

    private boolean observeOwner(ProcessIdentity owner) {
        if (applications.putIfAbsent(owner, MuteStatus.UNMUTED) == null) {
            ProcessIdentity.onExit(owner, () -> ownerExited(owner, "exit notification"));
        }
        return applications.containsKey(owner) && owner.equals(ProcessIdentity.find(owner.pid()));
    }

    /** Exit notifications and command-time identity checks share the same owner release. */
    private synchronized void ownerExited(ProcessIdentity owner, String reason) {
        applications.remove(owner);
        log.debug("Releasing audio owner {}: {}", owner, reason);
        for (var identity : List.copyOf(taps.keySet())) {
            var entry = taps.get(identity);
            if (entry == null || !entry.owner().equals(owner)) {
                continue;
            }
            if (!identity.equals(owner) && identity.equals(ProcessIdentity.find(identity.pid())) && entry.tap().gain() < FULL_VOLUME) {
                // The audio process genuinely survived. Release the departed owner's mute without
                // discarding this live tap's gain.
                entry.tap().setMuted(false);
                taps.put(identity, new Entry(entry.tap(), entry.outputDeviceId(), identity, entry.processObject()));
                observeOwner(identity);
                if (log.isDebugEnabled()) {
                    log.debug("Released owner mute; retaining live process={} tappedProcessObject={} currentProcessObject={} gain={}",
                            identity, entry.processObject(), wrapper.translatePidToProcessObject(identity.pid()), entry.tap().gain());
                }
            } else {
                closeEntry(identity, entry, "owner exited");
                taps.remove(identity);
            }
        }
    }

    private void closeEntry(ProcessIdentity identity, Entry entry, String reason) {
        log.debug("Closing audio tap: process={} owner={} processObject={} gain={} muted={} reason={}",
                identity, entry.owner(), entry.processObject(), entry.tap().gain(), entry.tap().muted(), reason);
        entry.tap().close();
    }

    private void cleanUpDeadProcesses() {
        for (var owner : List.copyOf(applications.keySet())) {
            if (!owner.equals(ProcessIdentity.find(owner.pid()))) {
                ownerExited(owner, "identity check");
            }
        }
        taps.entrySet().removeIf(control -> {
            if (control.getKey().equals(ProcessIdentity.find(control.getKey().pid()))) {
                return false;
            }
            closeEntry(control.getKey(), control.getValue(), "audio process exited or changed identity");
            return true;
        });
    }
}
