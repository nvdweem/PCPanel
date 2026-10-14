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
import java.util.function.IntPredicate;

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
 * {@link ProcessTap} per adjusted PID so a volume sticks while the user works in other apps, mirroring
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
 * <p>Every tap change happens under one lock, so a move, a dial change and shutdown never interleave.
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
    private final IntPredicate isAlive;
    private final boolean supported;
    private final Duration retryDelay;
    private final Object lock = new Object();
    @GuardedBy("lock") private final Map<Integer, Entry> taps = new HashMap<>();
    @GuardedBy("lock") private boolean destroyed;
    private final AtomicBoolean creationFailureLogged = new AtomicBoolean();
    private final ScheduledExecutorService migrator = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("tap-migration", true));
    private final AtomicBoolean migrationQueued = new AtomicBoolean();
    // Only touched on the migrator thread.
    private int retries;
    private @Nullable ScheduledFuture<?> retry;

    /**
     * The volume and mute wanted for one process, and the tap applying them to {@code outputDeviceId}. Without a
     * tap (it could not be recreated on any output) the process plays untouched until a retry succeeds.
     */
    private record Entry(@Nullable Tap tap, int outputDeviceId, float gain, boolean muted) {
        static Entry detached(float gain, boolean muted) {
            return new Entry(null, 0, gain, muted);
        }

        Entry with(float newGain, boolean newMuted) {
            if (tap != null) {
                tap.setGain(newGain);
                tap.setMuted(newMuted);
            }
            return new Entry(tap, outputDeviceId, newGain, newMuted);
        }

        void close() {
            if (tap != null) {
                tap.close();
            }
        }
    }

    /** What a tap for one process on one output needs, resolved before any existing tap is closed. */
    private record Target(int pid, int processObject, int outputDeviceId, String outputUid) {
    }

    @Inject
    OsxProcessTapService(CoreAudioWrapper wrapper) {
        this(wrapper, ProcessTap::create, pid -> ProcessHandle.of(pid).isPresent(), isSupported(), RETRY_DELAY);
    }

    OsxProcessTapService(CoreAudioWrapper wrapper, Tap.Factory tapFactory, IntPredicate isAlive, boolean supported, Duration retryDelay) {
        this.wrapper = wrapper;
        this.tapFactory = tapFactory;
        this.isAlive = isAlive;
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

    public void setVolume(int pid, float volume) {
        if (!supported) {
            log.trace("Process taps unavailable on this macOS version, not setting volume for {}", pid);
            return;
        }
        var gain = Math.clamp(volume, 0, 1);
        synchronized (lock) {
            if (destroyed) {
                return;
            }
            cleanUpDeadProcesses();
            taps.compute(pid, (key, entry) -> apply(pid, entry, gain, entry != null && entry.muted()));
        }
    }

    public void mute(int pid, MuteType muteType) {
        if (!supported) {
            log.trace("Process taps unavailable on this macOS version, not muting {}", pid);
            return;
        }
        synchronized (lock) {
            if (destroyed) {
                return;
            }
            cleanUpDeadProcesses();
            taps.compute(pid, (key, entry) -> apply(pid, entry, entry == null ? 1 : entry.gain(), muteType.convert(entry != null && entry.muted())));
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
    boolean migrateToDefaultOutput() {
        synchronized (lock) {
            if (destroyed) {
                return true;
            }
            for (var pid : List.copyOf(taps.keySet())) {
                taps.computeIfPresent(pid, (key, entry) -> refresh(pid, entry));
            }
            var output = wrapper.getDefaultDevice(true);
            return taps.values().stream().allMatch(entry -> entry.tap() != null && entry.outputDeviceId() == output);
        }
    }

    @PreDestroy
    void destroy() {
        migrator.shutdownNow();
        synchronized (lock) {
            destroyed = true;
            taps.values().forEach(Entry::close);
            taps.clear();
        }
    }

    /** The entry for {@code pid} after asking for {@code gain} and {@code muted}; null when the process plays untapped. */
    @GuardedBy("lock")
    private @Nullable Entry apply(int pid, @Nullable Entry entry, float gain, boolean muted) {
        if (gain >= FULL_VOLUME && !muted) {
            if (entry != null) {
                entry.close();
            }
            return null;
        }
        entry = refresh(pid, entry);
        if (entry != null) {
            return entry.with(gain, muted);
        }
        var target = resolve(pid, wrapper.getDefaultDevice(true));
        return target == null ? null : create(target, gain, muted);
    }

    /**
     * A tap renders to the device that was default at creation; recreate it when the default moved (or an earlier
     * move failed), and drop it when the process died. Everything the new tap needs is resolved before the old one
     * closes; when the new tap cannot be created the old output gets one back, so the app keeps its volume and
     * mute while the move is retried.
     */
    @GuardedBy("lock")
    private @Nullable Entry refresh(int pid, @Nullable Entry entry) {
        if (entry == null) {
            return null;
        }
        if (!isAlive.test(pid)) {
            entry.close();
            return null;
        }
        var output = wrapper.getDefaultDevice(true);
        if (output == 0 || entry.tap() != null && entry.outputDeviceId() == output) {
            return entry;
        }
        var target = resolve(pid, output);
        if (target == null) {
            return entry;
        }
        var previous = entry.tap() == null ? null : resolve(pid, entry.outputDeviceId());
        entry.close();
        var moved = create(target, entry.gain(), entry.muted());
        if (moved != null) {
            return moved;
        }
        var restored = previous == null ? null : create(previous, entry.gain(), entry.muted());
        log.debug("Unable to move the tap of pid {} to output {}, {}", pid, output, restored == null ? "untapped until a retry" : "kept on its previous output");
        return restored != null ? restored : Entry.detached(entry.gain(), entry.muted());
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

    private @Nullable Entry create(Target target, float gain, boolean muted) {
        try {
            var tap = tapFactory.create(target.processObject(), target.outputUid(), gain, muted);
            log.debug("Tapped pid {} (process object {}) on '{}'", target.pid(), target.processObject(), target.outputUid());
            return new Entry(tap, target.outputDeviceId(), gain, muted);
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

    @GuardedBy("lock")
    private void cleanUpDeadProcesses() {
        taps.entrySet().removeIf(entry -> {
            if (isAlive.test(entry.getKey())) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }
}
