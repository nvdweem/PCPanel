package com.getpcpanel.integration.volume.platform.osx;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.concurrent.AppThreads;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Per-process volume and mute on macOS via CoreAudio process taps (macOS 14.4+). Keeps one
 * {@link ProcessTap} per adjusted process so a volume sticks while the user works in other apps, mirroring
 * the Windows per-session behaviour. A volume at the top of the dial removes the tap entirely, handing
 * the audio path back to the OS (no re-render overhead, no lingering permission use).
 *
 * <p>A tap renders to the output that was default when it was created, so {@link #defaultOutputChanged()}
 * moves every tap to the new default output, keeping its volume and mute. The move runs on a thread of its own:
 * the notification arrives on CoreAudio's notification thread, where creating and stopping devices may block.
 * Moving closes the old tap before creating the new one (what a process does with two taps at once is not
 * documented), so the app plays untouched for the moment the new tap takes to start. A tap that cannot be moved
 * goes back to its previous output; until it reaches the new one it is retried a few times, again on the next
 * default-output change, and whenever its app's volume or mute is used.
 *
 * <p>Every tap change happens under this object's lock, so a move, a dial change, an app exit and shutdown never
 * interleave.
 *
 * <p>Requires the one-time "System Audio Recording" consent; when the user declines (or the macOS
 * version predates taps) every call degrades to a logged no-op.
 */
@Log4j2
@ApplicationScoped
@MacBuild
class OsxProcessTapService {
    /** Gains at/above this remove the tap: full volume is byte-identical to not being tapped at all. */
    private static final float FULL_VOLUME = 0.995f;
    static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    static final int MAX_RETRIES = 3;
    private final CoreAudioWrapper wrapper;
    private final Tap.Factory tapFactory;
    private final ProcessIdentity.Lookup processes;
    private final boolean supported;
    private final Duration retryDelay;
    @GuardedBy("this") private final Map<ProcessIdentity, Entry> taps = new HashMap<>();
    // Mute intent per application; an owner is in here exactly while it has an exit notification registered.
    @GuardedBy("this") private final Map<ProcessIdentity, MuteStatus> applications = new HashMap<>();
    @GuardedBy("this") private boolean destroyed;
    private final AtomicBoolean creationFailureLogged = new AtomicBoolean();
    private final ScheduledExecutorService migrator = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("tap-migration", true));
    private final AtomicBoolean migrationQueued = new AtomicBoolean();
    // Only touched on the migrator thread.
    private int retries;
    private @Nullable ScheduledFuture<?> retry;

    private enum MuteStatus { UNMUTED, PENDING, APPLIED }

    /**
     * The volume and mute wanted for one process, and the tap applying them to {@code outputDeviceId}. Without a
     * tap (it could not be recreated on any output) the process plays untouched until a retry succeeds.
     */
    private record Entry(@Nullable Tap tap, int outputDeviceId, ProcessIdentity owner, int processObject, float gain, boolean muted) {
        Entry with(float newGain, boolean newMuted, ProcessIdentity newOwner) {
            if (tap != null) {
                tap.setGain(newGain);
                tap.setMuted(newMuted);
            }
            return new Entry(tap, outputDeviceId, newOwner, processObject, newGain, newMuted);
        }

        Entry detached() {
            return new Entry(null, 0, owner, processObject, gain, muted);
        }

        /** Whether the process is muted right now, not merely meant to be. */
        boolean mutedNow() {
            return tap != null && muted;
        }
    }

    /** What a tap for one process on one output needs, resolved before any existing tap is closed. */
    private record Target(int pid, int processObject, int outputDeviceId, String outputUid) {
    }

    @Inject
    OsxProcessTapService(CoreAudioWrapper wrapper) {
        this(wrapper, ProcessTap::create, ProcessIdentity.Lookup.SYSTEM, isSupported(), RETRY_DELAY);
    }

    OsxProcessTapService(CoreAudioWrapper wrapper, Tap.Factory tapFactory, ProcessIdentity.Lookup processes, boolean supported, Duration retryDelay) {
        this.wrapper = wrapper;
        this.tapFactory = tapFactory;
        this.processes = processes;
        this.supported = supported;
        this.retryDelay = retryDelay;
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
        if (!supported) {
            return;
        }
        cleanUpDeadProcesses();
        var identity = processes.find(pid);
        if (identity == null) {
            return;
        }
        var entry = taps.get(identity);
        update(identity, entry == null ? identity : entry.owner(), volume, entry != null && entry.muted());
    }

    public synchronized boolean isMuted(int pid) {
        cleanUpDeadProcesses();
        var entry = taps.get(processes.find(pid));
        return entry != null && entry.mutedNow();
    }

    public synchronized boolean setMuted(int pid, boolean muted) {
        cleanUpDeadProcesses();
        var identity = processes.find(pid);
        var entry = taps.get(identity);
        return identity == null ? !muted : setMuted(entry == null ? identity : entry.owner(), identity, muted);
    }

    @GuardedBy("this")
    private boolean setMuted(ProcessIdentity owner, ProcessIdentity identity, boolean muted) {
        if (!supported) {
            return false;
        }
        var entry = taps.get(identity);
        update(identity, owner, entry == null ? 1 : entry.gain(), muted);
        return isMuted(identity.pid()) == muted;
    }

    public synchronized void mute(int pid, MuteType muteType) {
        setMuted(pid, muteType.convert(isMuted(pid)));
    }

    public synchronized void muteApplication(int ownerPid, List<Integer> pids, MuteType mute) {
        var owner = processes.find(ownerPid);
        var status = applications.get(owner);
        // Snapshot the live audio state before cleanup releases expired-owner mute. A late exit callback
        // must not turn the user's first "unmute" toggle into a new mute. The GUI PID may have no audio.
        var audioPids = pids.stream().filter(pid -> wrapper.translatePidToProcessObject(pid) != 0).toList();
        var audioEntries = audioPids.stream().map(pid -> taps.get(processes.find(pid))).toList();
        var expiredOwnerMute = audioEntries.stream().anyMatch(entry -> entry != null && entry.mutedNow()
                && !entry.owner().equals(processes.find(entry.owner().pid())));
        // A pending mute of an app that has no audio yet counts as muted, so toggling again cancels it. While the app
        // has audio that could not be muted, it is still heard, so a toggle tries to mute it again.
        var muted = status == MuteStatus.APPLIED || status == MuteStatus.PENDING && audioPids.isEmpty()
                || (status == null || status == MuteStatus.UNMUTED) && (expiredOwnerMute
                    || !audioEntries.isEmpty() && audioEntries.stream().allMatch(entry -> entry != null && entry.mutedNow()));
        cleanUpDeadProcesses();
        if (owner == null || !supported || !owner.equals(processes.find(ownerPid))) {
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
                var identity = processes.find(pid);
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
        var owner = processes.find(ownerPid);
        if (owner == null || applications.getOrDefault(owner, MuteStatus.UNMUTED) == MuteStatus.UNMUTED) {
            return;
        }
        var applied = applications.get(owner) == MuteStatus.APPLIED;
        for (var pid : pids) {
            var identity = processes.find(pid);
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
        var owner = processes.find(ownerPid);
        if (owner == null || !supported) {
            return;
        }
        var muted = applications.getOrDefault(owner, MuteStatus.UNMUTED) != MuteStatus.UNMUTED;
        for (var pid : pids) {
            var identity = processes.find(pid);
            if (identity != null) {
                var entry = taps.get(identity);
                update(identity, owner, volume, muted || entry != null && entry.muted());
            }
        }
        if (muted && pids.stream().anyMatch(this::isMuted)) {
            applications.put(owner, MuteStatus.APPLIED);
        }
    }

    /**
     * The default output changed: moves every tap to it. Called on CoreAudio's notification thread, so it only
     * queues the move; changes arriving while one is queued are covered by it.
     */
    void defaultOutputChanged() {
        if (!supported || !migrationQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            migrator.execute(this::migrateAfterChange);
        } catch (RejectedExecutionException e) {
            log.trace("Tap migration not queued, shutting down");
        }
    }

    /** A new change replaces whatever retries the previous one still had pending. */
    private void migrateAfterChange() {
        // Cleared before reading the default output, so a change during this run queues another one.
        migrationQueued.set(false);
        if (retry != null) {
            retry.cancel(false);
        }
        retries = 0;
        migrateOrRetry();
    }

    private void migrateOrRetry() {
        retry = null;
        boolean settled;
        try {
            settled = migrateToDefaultOutput();
        } catch (RuntimeException e) {
            log.warn("Unable to move per-app volume to the new default output", e);
            settled = false;
        }
        if (settled || retries >= MAX_RETRIES) {
            return;
        }
        try {
            // 1, 2, 4 × the delay: long enough for a just-connected output (Bluetooth) to come up.
            retry = migrator.schedule(this::migrateOrRetry, retryDelay.toMillis() << retries++, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.trace("Tap migration retry not scheduled, shutting down");
        }
    }

    /**
     * Recreates every tap that does not render to the current default output, keeping its volume and mute.
     *
     * @return whether every tap now renders to the default output
     */
    synchronized boolean migrateToDefaultOutput() {
        if (destroyed) {
            return true;
        }
        cleanUpDeadProcesses();
        for (var identity : List.copyOf(taps.keySet())) {
            var entry = refresh(identity, taps.get(identity));
            if (entry == null) {
                taps.remove(identity);
            } else {
                taps.put(identity, entry);
            }
        }
        var output = wrapper.getDefaultDevice(true);
        return taps.values().stream().allMatch(entry -> entry.tap() != null && entry.outputDeviceId() == output);
    }

    @PreDestroy
    void destroy() {
        migrator.shutdownNow();
        synchronized (this) {
            destroyed = true;
            taps.forEach((identity, entry) -> closeEntry(identity, entry, "shutdown"));
            taps.clear();
            applications.clear();
        }
    }

    /** Applies the requested state, creating, moving or removing the process's tap as needed. */
    @GuardedBy("this")
    private void update(ProcessIdentity identity, ProcessIdentity owner, float gain, boolean muted) {
        if (destroyed || !owner.equals(processes.find(owner.pid()))) {
            return;
        }
        var entry = taps.get(identity);
        if (entry != null && !entry.owner().equals(owner) && !observeOwner(owner)) {
            return;
        }
        if (gain >= FULL_VOLUME && !muted) {
            if (entry != null) {
                closeEntry(identity, entry, "full unmuted volume");
                taps.remove(identity);
            }
            return;
        }
        if (entry != null) {
            entry = refresh(identity, entry.with(gain, muted, owner));
            if (entry == null) {
                taps.remove(identity);
            } else {
                taps.put(identity, entry);
            }
            return;
        }
        if (!observeOwner(owner)) {
            return;
        }
        var target = resolve(identity.pid(), wrapper.getDefaultDevice(true));
        var created = target == null ? null : create(target, owner, gain, muted);
        if (created == null) {
            return;
        }
        // Do not retain a tap if the PID changed lifetime during its creation.
        if (identity.equals(processes.find(identity.pid())) && owner.equals(processes.find(owner.pid()))) {
            taps.put(identity, created);
        } else {
            closeEntry(identity, created, "process or owner changed during creation");
        }
    }

    /**
     * A tap renders to the device that was default at creation; recreate it when the default moved (or an earlier
     * move failed), and drop it when the process ended. Everything the new tap needs is resolved before the old one
     * closes; when the new tap cannot be created the old output gets one back, so the app keeps its volume and
     * mute while the move is retried.
     */
    @GuardedBy("this")
    private @Nullable Entry refresh(ProcessIdentity identity, Entry entry) {
        if (!identity.equals(processes.find(identity.pid()))) {
            closeEntry(identity, entry, "audio process exited or changed identity");
            return null;
        }
        var output = wrapper.getDefaultDevice(true);
        if (output == 0 || entry.tap() != null && entry.outputDeviceId() == output) {
            return entry;
        }
        var target = resolve(identity.pid(), output);
        if (target == null) {
            return entry;
        }
        var previous = entry.tap() == null ? null : resolve(identity.pid(), entry.outputDeviceId());
        closeEntry(identity, entry, "moving to output " + output);
        var moved = create(target, entry.owner(), entry.gain(), entry.muted());
        if (moved != null) {
            return moved;
        }
        var restored = previous == null ? null : create(previous, entry.owner(), entry.gain(), entry.muted());
        log.debug("Unable to move the tap of {} to output {}, {}", identity, output, restored == null ? "untapped until a retry" : "kept on its previous output");
        return restored != null ? restored : entry.detached();
    }

    private @Nullable Target resolve(int pid, int outputDeviceId) {
        var processObject = wrapper.translatePidToProcessObject(pid);
        if (processObject == 0) {
            log.debug("No CoreAudio process object for pid {}, not tapping", pid);
            return null;
        }
        var outputUid = outputDeviceId == 0 ? null : wrapper.deviceUid(outputDeviceId);
        if (outputUid == null) {
            log.debug("No UID for output device {}, not tapping pid {}", outputDeviceId, pid);
            return null;
        }
        return new Target(pid, processObject, outputDeviceId, outputUid);
    }

    private @Nullable Entry create(Target target, ProcessIdentity owner, float gain, boolean muted) {
        try {
            var tap = tapFactory.create(target.processObject(), target.outputUid(), gain, muted);
            log.debug("Tapped pid={} owner={} processObject={} output='{}' gain={} muted={}", target.pid(), owner, target.processObject(), target.outputUid(), gain, muted);
            return new Entry(tap, target.outputDeviceId(), owner, target.processObject(), gain, muted);
        } catch (RuntimeException e) {
            if (e instanceof ProcessTap.TapCreationException && creationFailureLogged.compareAndSet(false, true)) {
                log.warn("Unable to create a process tap for pid {}. Per-app volume needs the System Audio Recording permission: "
                        + "System Settings > Privacy & Security > Screen & System Audio Recording > enable PCPanel", target.pid(), e);
            } else {
                log.debug("Unable to create a process tap for pid {}", target.pid(), e);
            }
            return null;
        }
    }

    @GuardedBy("this")
    private boolean observeOwner(ProcessIdentity owner) {
        if (applications.putIfAbsent(owner, MuteStatus.UNMUTED) == null) {
            processes.onExit(owner, () -> ownerExited(owner, "exit notification"));
        }
        return applications.containsKey(owner) && owner.equals(processes.find(owner.pid()));
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
            if (!identity.equals(owner) && identity.equals(processes.find(identity.pid())) && entry.gain() < FULL_VOLUME) {
                // The audio process genuinely survived. Release the departed owner's mute without
                // discarding this live tap's gain.
                taps.put(identity, entry.with(entry.gain(), false, identity));
                observeOwner(identity);
                log.debug("Released owner mute; retaining live process={} tappedProcessObject={} gain={}", identity, entry.processObject(), entry.gain());
            } else {
                closeEntry(identity, entry, "owner exited");
                taps.remove(identity);
            }
        }
    }

    private static void closeEntry(ProcessIdentity identity, Entry entry, String reason) {
        log.debug("Closing audio tap: process={} owner={} processObject={} gain={} muted={} reason={}",
                identity, entry.owner(), entry.processObject(), entry.gain(), entry.muted(), reason);
        if (entry.tap() != null) {
            entry.tap().close();
        }
    }

    @GuardedBy("this")
    private void cleanUpDeadProcesses() {
        for (var owner : List.copyOf(applications.keySet())) {
            if (!owner.equals(processes.find(owner.pid()))) {
                ownerExited(owner, "identity check");
            }
        }
        taps.entrySet().removeIf(control -> {
            if (control.getKey().equals(processes.find(control.getKey().pid()))) {
                return false;
            }
            closeEntry(control.getKey(), control.getValue(), "audio process exited or changed identity");
            return true;
        });
    }
}
