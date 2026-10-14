package com.getpcpanel.integration.volume.platform.osx;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

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
 * moves every tap to the new default output, keeping its volume and mute. That happens on a thread of its own:
 * the notification arrives on CoreAudio's notification thread, where creating and stopping devices may block.
 * A tap that cannot be recreated keeps its volume and mute and is retried a few times, again on the next
 * default-output change, and whenever its app's volume or mute is used.
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
    private final Map<Integer, Entry> taps = new ConcurrentHashMap<>();
    private final AtomicBoolean creationFailureLogged = new AtomicBoolean();
    private final ScheduledExecutorService migrator = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("tap-migration", true));
    private final AtomicBoolean migrationQueued = new AtomicBoolean();
    private final AtomicInteger retries = new AtomicInteger();

    /**
     * The volume and mute wanted for one process, and the tap applying them to {@code outputDeviceId}. Without a
     * tap (it could not be recreated on a new output) the process plays untouched until a retry succeeds.
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
        cleanUpDeadProcesses();
        var gain = Math.clamp(volume, 0, 1);
        taps.compute(pid, (key, entry) -> apply(pid, entry, gain, entry != null && entry.muted()));
    }

    public void mute(int pid, MuteType muteType) {
        if (!supported) {
            log.trace("Process taps unavailable on this macOS version, not muting {}", pid);
            return;
        }
        cleanUpDeadProcesses();
        taps.compute(pid, (key, entry) -> apply(pid, entry, entry == null ? 1 : entry.gain(), muteType.convert(entry != null && entry.muted())));
    }

    /**
     * The default output changed: moves every tap to it. Called on CoreAudio's notification thread, so it only
     * queues the move; changes arriving while one is queued are covered by it.
     */
    void defaultOutputChanged() {
        if (!supported) {
            return;
        }
        retries.set(0);
        queueMigration();
    }

    private void queueMigration() {
        if (!migrationQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            migrator.execute(this::runMigration);
        } catch (RejectedExecutionException e) {
            log.trace("Tap migration not queued, shutting down");
        }
    }

    private void runMigration() {
        // Cleared before reading the default output, so a change during this run queues another one.
        migrationQueued.set(false);
        try {
            migrateToDefaultOutput();
        } catch (RuntimeException e) {
            log.warn("Unable to move per-app volume to the new default output", e);
        }
        var attempt = retries.incrementAndGet();
        if (attempt <= MAX_RETRIES && taps.values().stream().anyMatch(entry -> entry.tap() == null)) {
            try {
                // 1, 2, 4 × the delay: long enough for a just-connected output (Bluetooth) to come up.
                migrator.schedule(this::queueMigration, retryDelay.toMillis() << (attempt - 1), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                log.trace("Tap migration retry not scheduled, shutting down");
            }
        }
    }

    /** Recreates every tap that does not render to the current default output, keeping its volume and mute. */
    void migrateToDefaultOutput() {
        for (var pid : List.copyOf(taps.keySet())) {
            taps.computeIfPresent(pid, (key, entry) -> refresh(pid, entry));
        }
    }

    @PreDestroy
    void destroy() {
        migrator.shutdownNow();
        try {
            if (!migrator.awaitTermination(2, TimeUnit.SECONDS)) {
                log.debug("Tap migration still running at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        taps.values().forEach(Entry::close);
        taps.clear();
    }

    /** The entry for {@code pid} after asking for {@code gain} and {@code muted}; null when the process plays untapped. */
    private @Nullable Entry apply(int pid, @Nullable Entry entry, float gain, boolean muted) {
        if (gain >= FULL_VOLUME && !muted) {
            if (entry != null) {
                entry.close();
            }
            return null;
        }
        entry = refresh(pid, entry);
        if (entry == null) {
            return createEntry(pid, wrapper.getDefaultDevice(true), gain, muted);
        }
        return entry.with(gain, muted);
    }

    /**
     * A tap renders to the device that was default at creation; recreate it when the default moved (or a
     * recreation failed before), and drop it when the process died. A failed recreation keeps the wanted state.
     */
    private @Nullable Entry refresh(int pid, @Nullable Entry entry) {
        if (entry == null) {
            return null;
        }
        if (!isAlive.test(pid)) {
            entry.close();
            return null;
        }
        var defaultOutput = wrapper.getDefaultDevice(true);
        if (defaultOutput == 0 || entry.tap() != null && entry.outputDeviceId() == defaultOutput) {
            return entry;
        }
        entry.close();
        var recreated = createEntry(pid, defaultOutput, entry.gain(), entry.muted());
        if (recreated == null) {
            log.debug("Unable to move the tap of pid {} to output {}, retrying later", pid, defaultOutput);
            return Entry.detached(entry.gain(), entry.muted());
        }
        return recreated;
    }

    private @Nullable Entry createEntry(int pid, int outputDeviceId, float gain, boolean muted) {
        var processObject = wrapper.translatePidToProcessObject(pid);
        if (processObject == 0) {
            log.debug("No CoreAudio process object for pid {}, not tapping", pid);
            return null;
        }
        var outputUid = outputDeviceId == 0 ? null : wrapper.deviceUid(outputDeviceId);
        if (outputUid == null) {
            log.warn("No default output device UID, not tapping pid {}", pid);
            return null;
        }
        try {
            var tap = tapFactory.create(processObject, outputUid, gain);
            tap.setMuted(muted);
            log.debug("Tapped pid {} (process object {}) on '{}'", pid, processObject, outputUid);
            return new Entry(tap, outputDeviceId, gain, muted);
        } catch (RuntimeException e) {
            if (e instanceof ProcessTap.TapCreationException && creationFailureLogged.compareAndSet(false, true)) {
                log.warn("Unable to create a process tap for pid {}. Per-app volume needs the System Audio Recording permission: "
                        + "System Settings > Privacy & Security > Screen & System Audio Recording > enable PCPanel", pid, e);
            } else {
                log.debug("Unable to create a process tap for pid {}", pid, e);
            }
            return null;
        }
    }

    private void cleanUpDeadProcesses() {
        for (var pid : List.copyOf(taps.keySet())) {
            if (!isAlive.test(pid)) {
                taps.computeIfPresent(pid, (key, entry) -> {
                    entry.close();
                    return null;
                });
            }
        }
    }
}
