package com.getpcpanel.integration.volume.platform.osx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<Integer, Entry> taps = new ConcurrentHashMap<>();
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

    public void setVolume(int pid, float volume) {
        if (!isSupported()) {
            log.trace("Process taps unavailable on this macOS version, not setting volume for {}", pid);
            return;
        }
        cleanUpDeadProcesses();
        taps.compute(pid, (key, entry) -> {
            entry = recreateIfStale(pid, entry);
            if (entry == null) {
                return volume >= FULL_VOLUME ? null : createEntry(pid, volume, false);
            }
            entry.tap().setGain(volume);
            if (volume >= FULL_VOLUME && !entry.tap().muted()) {
                entry.tap().close();
                return null;
            }
            return entry;
        });
    }

    public void mute(int pid, MuteType muteType) {
        if (!isSupported()) {
            log.trace("Process taps unavailable on this macOS version, not muting {}", pid);
            return;
        }
        cleanUpDeadProcesses();
        taps.compute(pid, (key, entry) -> {
            entry = recreateIfStale(pid, entry);
            var target = muteType.convert(entry != null && entry.tap().muted());
            if (entry == null) {
                return target ? createEntry(pid, 1, true) : null;
            }
            entry.tap().setMuted(target);
            if (!target && entry.tap().gain() >= FULL_VOLUME) {
                entry.tap().close();
                return null;
            }
            return entry;
        });
    }

    @PreDestroy
    void destroy() {
        taps.values().forEach(entry -> entry.tap().close());
        taps.clear();
    }

    /** A tap renders to the device that was default at creation; recreate when the default moved or the process died. */
    private Entry recreateIfStale(int pid, Entry entry) {
        if (entry == null) {
            return null;
        }
        if (ProcessHandle.of(pid).isEmpty()) {
            entry.tap().close();
            return null;
        }
        if (entry.outputDeviceId() == wrapper.getDefaultDevice(true)) {
            return entry;
        }
        var gain = entry.tap().gain();
        var muted = entry.tap().muted();
        entry.tap().close();
        return createEntry(pid, gain, muted);
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
            if (ProcessHandle.of(entry.getKey()).isPresent()) {
                return false;
            }
            entry.getValue().tap().close();
            return true;
        });
    }
}
