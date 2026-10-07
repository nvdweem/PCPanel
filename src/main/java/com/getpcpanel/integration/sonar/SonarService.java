package com.getpcpanel.integration.sonar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.getpcpanel.commands.IntegrationConnection;
import com.getpcpanel.commands.NestedCommands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.sonar.command.CommandSonar;
import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.util.concurrent.AppThreads;
import com.getpcpanel.util.concurrent.ReconnectBackoff;
import com.getpcpanel.util.concurrent.ReconnectOnUse;

import re.walk.sonar.ISonarClient;
import re.walk.sonar.SonarClient;
import re.walk.sonar.model.SonarChannel;
import re.walk.sonar.model.SonarLevel;
import re.walk.sonar.model.SonarMix;
import re.walk.sonar.model.SonarMode;
import re.walk.sonar.model.SonarRoute;
import re.walk.sonar.model.SonarState;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Owns PCPanel's view of Sonar: settings, readiness, the cached snapshot and the poll that keeps it
 * current. The poll exists only because Sonar offers no push — unlike Wave Link, which subscribes and
 * is told. Local writes are authoritative the moment they happen, so the poll's sole job is noticing
 * changes made in the GG client.
 *
 * <p>While Sonar is enabled the poll always finds GG and reads the mode, one loopback request a second,
 * so the settings and the action picker can report whether Sonar is there before any control uses it.
 * The full level read runs only while a control targets Sonar ({@link #isInUse()}).
 *
 * <p>Volume writes are coalesced per {@link SonarRoute}: at most {@link SonarSettings#updatesPerSecond()}
 * HTTP writes a second per route, always carrying the latest value, and sent only by the flush thread so
 * the thread handling dial input never blocks on the network. Each write costs SteelSeries GG CPU, which
 * is why the rate is the user's choice. A mute change goes out on the next tick instead, since a press is
 * one write and should not wait out a sweep's interval. The flush thread sends one write at a time and
 * Sonar answers each in about 30–40 ms, so a single mix tops out near 25–30 writes a second, and a Both
 * control on Master, which writes two mixes per interval, near 12 a second per mix, whatever the setting.
 *
 * <p>Every read-modify-write of the cached
 * state, together with the write bookkeeping, happens under {@code stateLock}, so a poll and a dial
 * write cannot overwrite each other's result; events are fired outside it.
 */
@Log4j2
@ApplicationScoped
public class SonarService implements IntegrationConnection {
    /**
     * How often the flush thread looks for due writes while any are queued. Fine enough that every
     * coalescing interval the update rate allows (40–167 ms) is kept to within one tick.
     */
    private static final long FLUSH_TICK_MS = 10;
    /** A poll result for a route written within this window is discarded. Longer than the poll interval
     *  so a poll already in flight when the write lands cannot win. */
    private static final long WRITE_PRECEDENCE_MS = 2_000;

    private final ISonarClient client;
    private final SaveService saveService;
    /**
     * Spaces out looking for Sonar while it cannot be found (GG closed, or Sonar not running in it). The cap
     * is short: GG usually starts with Windows, and finding it is a local file read and a loopback request.
     */
    private final ReconnectBackoff discoveryBackoff = new ReconnectBackoff(2_000, 10_000);
    /** Using a Sonar control clears {@link #discoveryBackoff}, so the next one-second poll looks for Sonar. */
    private final ReconnectOnUse reconnectOnUse = new ReconnectOnUse("sonar", ReconnectOnUse.DEFAULT_COOLDOWN_MS,
            () -> isEnabled() && !isReady(), discoveryBackoff::reset);
    @Nullable private final Event<SonarChangedEvent> changed;
    private final AtomicReference<SonarState> state = new AtomicReference<>(SonarState.UNKNOWN);
    private final AtomicBoolean inUse = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final Map<SonarRoute, Pending> pending = new ConcurrentHashMap<>();
    /** Guarded by {@code stateLock}. */
    private final Map<SonarRoute, Long> lastWrittenAt = new HashMap<>();
    /**
     * Runs {@link #flushTick()} {@value #FLUSH_TICK_MS} ms after the previous one ended, for as long as
     * writes are queued, and not at all while none are. A dedicated thread rather than {@code @Scheduled},
     * whose scheduler checks its triggers once a second. One thread with one tick scheduled at a time never
     * runs two flushes at once: a slow write only postpones the next tick. Null when the caller flushes by
     * hand.
     */
    @Nullable private final ScheduledExecutorService flushExecutor;
    /** Set while a tick is scheduled or running; only a tick that finds nothing queued clears it. */
    private final AtomicBoolean tickScheduled = new AtomicBoolean();
    private volatile boolean shuttingDown;

    /** A write waiting out its coalescing window; a null field is left untouched on Sonar. */
    private record Pending(@Nullable Double volume, @Nullable Boolean muted, long dueAt) {
        Pending mergedWith(Pending newer) {
            return new Pending(newer.volume() != null ? newer.volume() : volume,
                               newer.muted() != null ? newer.muted() : muted,
                               Math.min(dueAt, newer.dueAt()));
        }
    }

    @Inject
    public SonarService(SaveService saveService, Event<SonarChangedEvent> changed) {
        this(new SonarClient(), saveService, changed,
             Executors.newSingleThreadScheduledExecutor(AppThreads.factory("sonar-flush", true)));
    }

    protected SonarService(ISonarClient client, SaveService saveService, @Nullable Event<SonarChangedEvent> changed,
                 @Nullable ScheduledExecutorService flushExecutor) {
        this.client = client;
        this.saveService = saveService;
        this.changed = changed;
        this.flushExecutor = flushExecutor;
    }

    public boolean isEnabled() {
        return saveService.get().getSonar().enabled();
    }

    @Override
    public boolean owns(Command command) {
        return command instanceof CommandSonar;
    }

    @Override
    public void onUsed() {
        reconnectOnUse.request();
    }

    /** True once a poll has found Sonar and read its mode; commands check this before writing. */
    public boolean isReady() {
        return state.get().mode() != null;
    }

    /** Set when at least one configured control targets Sonar, so levels are only read while something uses them. */
    public void setInUse(boolean value) {
        inUse.set(value);
    }

    public boolean isInUse() {
        return inUse.get();
    }

    /**
     * The level-read gate is derived from the save file, not from which controls happen to be asked to
     * resolve a mute colour: {@link SonarMuteResolver} only runs for a control that already has a
     * mute-override colour configured, so gating on it would leave the levels permanently unread for a
     * control with no such colour (or before the mute-colour layer ever consults
     * Sonar at all). Recomputing from the save instead is correct in both directions — a freshly
     * added Sonar control opens the gate, and removing the last one closes it — and fires whenever
     * {@link SaveService} fires {@link SaveEvent}: on startup (the initial load) and after every save,
     * which covers every place a control's commands can change.
     *
     * <p>Switching the integration off drops it at once, like Wave Link's disconnect: readiness goes, so
     * commands stop writing and the status stops reporting a connection.
     */
    public void onSaveChanged(@Observes SaveEvent event) {
        setInUse(hasSonarCommand(event.save()));
        if (!event.save().getSonar().enabled()) {
            resetToUnknown();
        }
    }

    private static boolean hasSonarCommand(Save save) {
        return StreamEx.of(save.getDevices().values())
                       .flatMap(ds -> StreamEx.of(ds.getProfiles()))
                       .flatMap(SonarService::controlCommands)
                       .anyMatch(CommandSonar.class::isInstance);
    }

    /** Every place a profile can hold a control's commands: dial, button, double-click, release and hold. */
    private static StreamEx<Command> controlCommands(Profile profile) {
        return StreamEx.of(profile.getDialData().values())
                       .append(profile.getButtonData().values())
                       .append(profile.getDblButtonData().values())
                       .append(profile.getReleaseButtonData().values())
                       .append(profile.getHoldButtonData().values())
                       .nonNull()
                       .flatMap(c -> StreamEx.of(c.getCommands()))
                       .flatMap(SonarService::withNested);
    }

    /**
     * A command that runs commands of its own ({@link NestedCommands}: the bands of a stepped switch, the up/down
     * actions of a per-step dial) can hold a Sonar command that is only reachable by descending into it; recurses
     * in case those nest another level.
     */
    private static StreamEx<Command> withNested(Command command) {
        if (command instanceof NestedCommands nested) {
            return StreamEx.of(nested.nestedCommands())
                           .flatMap(c -> StreamEx.of(c.getCommands()))
                           .flatMap(SonarService::withNested)
                           .prepend(command);
        }
        return StreamEx.of(command);
    }

    /** Falls back to {@code stream} only before the first poll settles a real mode; callers check {@link #isReady()} before writing. */
    public SonarRoute route(SonarChannel channel, SonarMix mix) {
        return routeFor(state.get(), channel, mix);
    }

    public Optional<SonarLevel> level(SonarChannel channel, SonarMix mix) {
        var snapshot = state.get();
        return snapshot.level(routeFor(snapshot, channel, mix));
    }

    private static SonarRoute routeFor(SonarState snapshot, SonarChannel channel, SonarMix mix) {
        var mode = Optional.ofNullable(snapshot.mode()).orElse(SonarMode.stream);
        return SonarRoute.of(mode, mix, channel);
    }

    /** Null when unknown — {@link com.getpcpanel.integration.volume.mutecolor.MuteStateResolver} maps that to empty. */
    @Nullable
    public Boolean mutedOrNull(SonarChannel channel, SonarMix mix) {
        return level(channel, mix).map(SonarLevel::muted).orElse(null);
    }

    /**
     * Muted only when every selected mix is muted, so {@link SonarMixSelection#both} reads as muted exactly
     * when a mute of both would change nothing. Null when any selected mix's level is unknown.
     */
    @Nullable
    public Boolean mutedOrNull(SonarChannel channel, SonarMixSelection selection) {
        var snapshot = state.get();
        var muted = true;
        for (var mix : selection.mixes()) {
            var level = snapshot.level(routeFor(snapshot, channel, mix)).orElse(null);
            if (level == null) {
                return null;
            }
            muted &= level.muted();
        }
        return muted;
    }

    /** Writes every selected mix; in Classic mode they share a route, so that is one write. */
    public void setVolume(SonarChannel channel, SonarMixSelection selection, double value) {
        var now = System.currentTimeMillis();
        for (var mix : selection.mixes()) {
            setVolumeAt(channel, mix, value, now);
        }
    }

    /** Writes every selected mix; in Classic mode they share a route, so that is one write. */
    public void setMute(SonarChannel channel, SonarMixSelection selection, boolean muted) {
        var now = System.currentTimeMillis();
        for (var mix : selection.mixes()) {
            setMuteAt(channel, mix, muted, now);
        }
    }

    /** Updates the cache at once; the HTTP write is queued. No-op until a mode is known. */
    public void setVolume(SonarChannel channel, SonarMix mix, double value) {
        setVolumeAt(channel, mix, value, System.currentTimeMillis());
    }

    /** Updates the cache and fires at once, so the mute colour follows; the HTTP write is queued. No-op until a mode is known. */
    public void setMute(SonarChannel channel, SonarMix mix, boolean muted) {
        setMuteAt(channel, mix, muted, System.currentTimeMillis());
    }

    void setVolumeAt(SonarChannel channel, SonarMix mix, double value, long now) {
        write(channel, mix, value, null, now);
    }

    void setMuteAt(SonarChannel channel, SonarMix mix, boolean muted, long now) {
        write(channel, mix, null, muted, now);
    }

    /**
     * Fires only when the route's mute flag changed: the one observer, the mute-colour layer, recomputes
     * every control under a lock, which a dial sweep must not trigger on every tick of the command thread.
     *
     * <p>A route with no cached level (Sonar found, levels not read yet) still gets its HTTP write, but
     * the cache is left alone: filling in the half the write does not carry would invent a mute state,
     * which the mute colour would show and a toggle would flip. The next poll supplies the real level.
     */
    private void write(SonarChannel channel, SonarMix mix, @Nullable Double volume, @Nullable Boolean muted, long now) {
        var muteChanged = false;
        synchronized (stateLock) {
            var current = state.get();
            if (current.mode() == null) {
                return;
            }
            var route = routeFor(current, channel, mix);
            var existing = current.level(route).orElse(null);
            if (existing != null) {
                var updated = new SonarLevel(volume != null ? volume : existing.volume(),
                                             muted != null ? muted : existing.muted());
                muteChanged = existing.muted() != updated.muted();
                if (!existing.equals(updated)) {
                    var levels = new HashMap<>(current.levels());
                    levels.put(route, updated);
                    state.set(new SonarState(current.mode(), Map.copyOf(levels)));
                }
                lastWrittenAt.put(route, now);
            }
            // Keyed on the route, so in Classic mode both mixes of a channel share one pending write. A mute
            // change is due at once; a volume queued on the same route goes out with it.
            var dueAt = muted != null ? now : now + coalesceMs();
            pending.merge(route, new Pending(volume, muted, dueAt), Pending::mergedWith);
        }
        startFlushing();
        if (muteChanged) {
            fireChanged();
        }
    }

    /**
     * At most one write per route per this many ms; the trailing value of a sweep is always sent. Read from
     * the settings on every write, so a change in Settings applies to the next write.
     */
    private long coalesceMs() {
        return Math.round(1000.0 / saveService.get().getSonar().updatesPerSecond());
    }

    /** Called after a write is queued: starts the ticker unless a tick is already scheduled or running. */
    private void startFlushing() {
        if (flushExecutor != null && !shuttingDown && tickScheduled.compareAndSet(false, true)) {
            scheduleTick();
        }
    }

    private void scheduleTick() {
        if (flushExecutor == null || shuttingDown) {
            return;
        }
        try {
            flushExecutor.schedule(this::flushTick, FLUSH_TICK_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Sonar: flush executor stopped, write not scheduled");
        }
    }

    /**
     * One flush, then the next tick only while anything is still queued. The flag is cleared before looking
     * at the queue: a write queued before the clear found the flag set and left scheduling to this tick, which
     * then sees it; a write queued after the clear takes the flag itself. Only the one that wins the flag
     * schedules, so a write is never stranded and two ticks are never pending at once.
     */
    void flushTick() {
        flushDue();
        tickScheduled.set(false);
        if (!pending.isEmpty() && tickScheduled.compareAndSet(false, true)) {
            scheduleTick();
        }
    }

    void onShutdown(@Observes ShutdownEvent event) {
        shuttingDown = true; // a write arriving during shutdown must not schedule on a stopped executor
        if (flushExecutor != null) {
            flushExecutor.shutdownNow();
        }
    }

    /** Catches everything: an exception escaping the tick would stop it from scheduling the next one. */
    void flushDue() {
        try {
            flushDue(System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.warn("Sonar: flush failed", e);
        }
    }

    void flushDue(long now) {
        if (pending.isEmpty()) {
            return;
        }
        for (var route : List.copyOf(pending.keySet())) {
            var queued = pending.get(route);
            if (queued == null || queued.dueAt() > now) {
                continue;
            }
            // Take whatever is queued for the route right now: a value merged in since the loop began is the one
            // to send. Only the flush that takes the entry out sends it, and a write after this starts a new entry.
            var p = pending.remove(route);
            if (p == null) {
                continue;
            }
            if (p.volume() != null) {
                client.setVolume(route, p.volume());
            }
            if (p.muted() != null) {
                client.setMute(route, p.muted());
            }
        }
    }

    /** SKIP: discovery, mode and levels can take seconds under timeouts, and an overlapping poll could apply an older result. */
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void poll() {
        poll(System.currentTimeMillis());
    }

    /**
     * Once Sonar is found every tick reads it, since polling is the only way to see changes made in GG.
     * While it is not found, looking for it again waits out {@link #discoveryBackoff}.
     */
    void poll(long now) {
        if (!isEnabled()) {
            discoveryBackoff.onSuccess(); // nothing to find → keep the gate clear so enabling finds Sonar at once
            resetToUnknown();
            return;
        }
        if (!isReady() && !discoveryBackoff.ready(now)) {
            return;
        }
        var mode = fetchModeReResolving();
        if (mode == null) {
            discoveryBackoff.onFailure(now);
            resetToUnknown();
            return;
        }
        discoveryBackoff.onSuccess();
        if (!inUse.get()) {
            // Nothing reads the levels, so none are kept: a cached level would only go stale.
            applyPolled(new SonarState(mode, Map.of()));
            return;
        }
        client.fetchState(mode).ifPresent(this::applyPolled);
    }

    /**
     * Reads the mode, and on failure drops the cached address and asks once more: GG assigns Sonar's
     * port per launch, so a failure is most often a GG restart, and re-resolving in the same tick keeps a
     * single failed request from dropping the integration and the writes queued on it.
     */
    @Nullable
    private SonarMode fetchModeReResolving() {
        var mode = client.fetchMode();
        if (mode.isPresent()) {
            return mode.get();
        }
        client.invalidate();
        return client.fetchMode().orElse(null);
    }

    /**
     * Sonar went away or was switched off: queued writes are dropped, since whatever answers next may run
     * in another mode.
     */
    private void resetToUnknown() {
        SonarState previous;
        synchronized (stateLock) {
            previous = state.getAndSet(SonarState.UNKNOWN);
            pending.clear();
            lastWrittenAt.clear();
        }
        if (!previous.equals(SonarState.UNKNOWN)) {
            fireChanged();
        }
    }

    private void applyPolled(SonarState polled) {
        applyPolledAt(polled, System.currentTimeMillis());
    }

    /**
     * Takes a poll result, except for routes written within {@link #WRITE_PRECEDENCE_MS}: those keep the
     * locally written value, which is newer than anything a poll in flight can carry. Only routes of the
     * polled mode are kept that way, and writes still queued for another mode are dropped — after a mode
     * switch in GG those routes no longer exist.
     */
    void applyPolledAt(SonarState polled, long now) {
        SonarState previous;
        SonarState merged;
        synchronized (stateLock) {
            previous = state.get();
            pending.keySet().removeIf(route -> route.mode() != polled.mode());
            lastWrittenAt.entrySet().removeIf(written -> written.getKey().mode() != polled.mode()
                    || now - written.getValue() >= WRITE_PRECEDENCE_MS);
            var levels = new HashMap<>(polled.levels());
            for (var route : lastWrittenAt.keySet()) {
                previous.level(route).ifPresent(ours -> levels.put(route, ours));
            }
            merged = new SonarState(polled.mode(), Map.copyOf(levels));
            state.set(merged);
        }
        if (!previous.equals(merged)) {
            fireChanged();
        }
    }

    void fireChanged() {
        if (changed != null) {
            changed.fire(new SonarChangedEvent());
        }
    }

    /** The cached snapshot — never triggers a poll, so it is safe to read from a UI request thread. */
    public SonarState snapshot() {
        return state.get();
    }

    void replaceState(SonarState newState) {
        state.set(newState);
    }
}
