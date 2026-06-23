package com.getpcpanel.wavelink;

import java.net.ConnectException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.util.Debouncer;
import com.getpcpanel.util.ReconnectBackoff;
import com.getpcpanel.volume.IFocusRedirector;

import jakarta.enterprise.inject.Instance;

import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.WaveLinkClient;
import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class WaveLinkService extends WaveLinkClient implements IWaveLinkClientEventListener, IFocusRedirector {
    private final SaveService saveService;
    /** Spaces out reconnect attempts when Wave Link is down (base = the scheduled interval, capped at 5 min). */
    private final ReconnectBackoff backoff = new ReconnectBackoff(10_000, 300_000);
    private boolean wasEnabled;
    @Inject
    Event<WaveLinkChangedEvent> changedEvent;
    @Inject
    WaveLinkAppCache appCache;
    @Inject
    Instance<ISndCtrl> sndCtrl;
    @Inject
    Debouncer debouncer;

    /**
     * Bridges OS process → Wave Link app identity. Wave Link names apps by a friendly name ("Microsoft
     * Edge") that rarely matches the OS executable ("msedge.exe"), so the OS-focused process can't be
     * matched to channel membership by name alone. Whenever Wave Link pushes a focus change we learn the
     * pairing — at that moment the OS foreground IS the app Wave Link just named — keyed by the
     * normalised executable. The focus-volume decision then maps the focused process to its Wave Link
     * identity and looks that up in the live channels, so adding/removing the app from a channel switches
     * control immediately. Keyed in-memory; rebuilt as Wave Link reports focus.
     */
    private final Map<String, WaveLinkApp> focusIdentityByProcess = new ConcurrentHashMap<>();

    WaveLinkService() {
        super(false);
        saveService = null;
    }

    /**
     * Routes the focused-app volume to Wave Link when the focused app is one Wave Link currently
     * controls, returning {@code true} to stop the coordinator from also changing the app's OS volume.
     *
     * <p>While connected the live channels are authoritative (so adding/removing the app from a channel
     * switches control immediately); when not yet connected — notably the startup race where the initial
     * focus-volume trigger fires before Wave Link is up — we defer to {@link WaveLinkAppCache} so a known
     * Wave-Link app's OS volume is never touched.
     */
    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        if (!focusRedirectEnabled()) {
            return false; // feature off → the OS controls focus volume normally
        }
        if (isConnected()) {
            var channelId = resolveChannelForFocusApp(targetProcess);
            if (channelId.isPresent()) {
                setChannelLevel(channelId.get(), volume);
                if (appCache != null) {
                    appCache.learn(targetProcess);
                }
                return true;
            }
            return false; // connected but the focused app is in no channel → let the OS handle its volume
        }
        // Not connected yet: defer to Wave Link for apps we have previously seen it control.
        return controlledWhileDisconnected(targetProcess);
    }

    @Override
    public boolean controlsFocusApp(String targetProcess) {
        if (!focusRedirectEnabled()) {
            return false;
        }
        if (isConnected()) {
            return resolveChannelForFocusApp(targetProcess).isPresent();
        }
        return controlledWhileDisconnected(targetProcess);
    }

    /** Whether the focus-control feature (route a focused Wave-Link app's volume to Wave Link) is on. */
    private boolean focusRedirectEnabled() {
        return saveService == null || saveService.get().getWaveLink().focusVolumeRedirect();
    }

    /** Whether Wave Link currently controls the app (it is in a channel), regardless of the focus-redirect setting. */
    @Override
    public boolean managesFocusApp(String targetProcess) {
        return isConnected() && resolveChannelForFocusApp(targetProcess).isPresent();
    }

    private boolean controlledWhileDisconnected(String targetProcess) {
        return appCache != null && appCache.isControlled(targetProcess);
    }

    /** Learns the OS-process → Wave Link app identity each time Wave Link reports a focus change. */
    @Override
    public void focusedAppChanged(WaveLinkApp app) {
        if (sndCtrl != null && sndCtrl.isResolvable()) {
            // Sampled inline (not debounced): the OS foreground IS the app Wave Link just named, at this
            // instant — deferring it would let focus drift and corrupt the identity pairing.
            learnFocusIdentity(app, sndCtrl.get().getFocusApplication());
        }
        // Debounce the enforce off the Wave Link websocket callback thread (matching fireChanged): on
        // Linux it spawns external processes to resolve the focused window, which must not block the
        // listener thread that gates further Wave Link message delivery.
        if (debouncer != null) {
            debouncer.debounce(ENFORCE_VOLUME_KEY, this::enforceFocusedControlledVolume, 250, TimeUnit.MILLISECONDS);
        } else {
            enforceFocusedControlledVolume(); // a controlled app just gained focus → pin its OS volume
        }
    }

    /**
     * Pins the focused app's OS volume to the configured percent when it is Wave-Link-controlled and the
     * "set controlled volume" setting is on — so e.g. an app left at 50% jumps to 100% once Wave Link
     * controls it (Wave Link then does the real mixing). Scoped to the focused app, where we can address
     * the OS volume directly ({@code setFocusVolume}) without mapping Wave Link's name to a process. Only
     * sets when the current volume actually differs, to avoid needless churn on repeated Wave Link events.
     */
    void enforceFocusedControlledVolume() {
        if (saveService == null || sndCtrl == null || !sndCtrl.isResolvable() || !isConnected()) {
            return;
        }
        var settings = saveService.get().getWaveLink();
        if (!settings.enforceControlledVolume()) {
            return;
        }
        var snd = sndCtrl.get();
        var focus = snd.getFocusApplication();
        if (StringUtils.isBlank(focus) || resolveChannelForFocusApp(focus).isEmpty()) {
            return; // nothing focused, or the focused app is not Wave-Link-controlled
        }
        var target = Math.max(0, Math.min(100, settings.controlledVolumePercent())) / 100f;
        var key = WaveLinkAppCache.normalize(focus);
        var current = StreamEx.of(snd.getAllSessions())
                              .filter(s -> s.executable() != null && key.equals(WaveLinkAppCache.normalize(s.executable().getName())))
                              .map(AudioSession::volume)
                              .findFirst().orElse(-1f);
        if (Math.abs(current - target) > 0.01f) {
            snd.setFocusVolume(target);
        }
    }

    /** Read-only snapshot of the learned OS-process → Wave Link app-name map (diagnostic). */
    public Map<String, String> focusIdentitySnapshot() {
        return StreamEx.of(focusIdentityByProcess.entrySet()).toMap(Map.Entry::getKey, e -> e.getValue().name());
    }

    /** Records that {@code osFocusPath} (the OS-focused executable) is the Wave Link app {@code app}. */
    void learnFocusIdentity(WaveLinkApp app, String osFocusPath) {
        if (app == null || app.isEmpty()) {
            return;
        }
        var key = WaveLinkAppCache.normalize(osFocusPath);
        if (!key.isBlank()) {
            focusIdentityByProcess.put(key, app);
            if (appCache != null) {
                // Persist the pairing so the next launch can bridge this process to its Wave Link app
                // before any focus change re-learns it (the in-memory map starts empty each session).
                appCache.learnIdentity(osFocusPath, app);
            }
        }
    }

    /**
     * The Wave Link app identity for {@code targetProcess}: the one learned live this session if present,
     * otherwise the persisted pairing from a previous session. Bridges the exe-vs-friendly-name gap
     * (msedge.exe → "Microsoft Edge") that name-only matching against channel membership can't.
     */
    @Nullable
    private WaveLinkApp identityFor(String targetProcess) {
        var live = focusIdentityByProcess.get(WaveLinkAppCache.normalize(targetProcess));
        if (live != null) {
            return live;
        }
        return appCache == null ? null : appCache.identity(targetProcess);
    }

    /**
     * The live channel that controls {@code targetProcess}'s volume, if any. Resolved against the
     * current channels (so it reflects add/remove immediately): first via the Wave Link identity for this
     * process — learned live this session or {@linkplain WaveLinkAppCache#identity persisted from a
     * previous one} — which bridges the exe-vs-friendly-name gap (e.g. msedge.exe → "Microsoft Edge"),
     * then by directly matching the executable name against channel apps for apps whose names happen to
     * match.
     */
    private Optional<String> resolveChannelForFocusApp(String targetProcess) {
        var identity = identityFor(targetProcess);
        if (identity != null) {
            var channel = findChannelIdForApp(identity);
            if (channel.isPresent()) {
                return channel;
            }
        }
        return findChannelIdForProcess(targetProcess);
    }

    /** Finds the channel whose membership includes the given OS process (matched by executable name). */
    private Optional<String> findChannelIdForProcess(String targetProcess) {
        var key = WaveLinkAppCache.normalize(targetProcess);
        if (key.isBlank()) {
            return Optional.empty();
        }
        return StreamEx.ofValues(getChannels())
                       .filter(channel -> StreamEx.of(channel.apps())
                                                  .anyMatch(app -> key.equals(WaveLinkAppCache.normalize(app.id()))
                                                          || key.equals(WaveLinkAppCache.normalize(app.name()))))
                       .map(WaveLinkChannel::id)
                       .findFirst();
    }

    /** Finds the channel whose membership includes an app matching {@code identity} (by id or name). */
    private Optional<String> findChannelIdForApp(WaveLinkApp identity) {
        if (identity == null || identity.isEmpty()) {
            return Optional.empty();
        }
        return StreamEx.ofValues(getChannels())
                       .filter(channel -> StreamEx.of(channel.apps()).anyMatch(app -> appsMatch(identity, app)))
                       .map(WaveLinkChannel::id)
                       .findFirst();
    }

    @Inject
    public WaveLinkService(SaveService saveService) {
        super(false);
        this.saveService = saveService;
        wasEnabled = isEnabled();
        addListener(this);
    }

    public boolean isEnabled() {
        return saveService.get().getWaveLink().enabled();
    }

    public void settingsChanged(@Observes SaveEvent event) {
        var is = isEnabled();
        if (wasEnabled && !is) {
            disconnect();
        }
        if (!wasEnabled && is) {
            reconnect();
        }
        wasEnabled = isEnabled();
    }

    @Scheduled(every = "10s")
    public void checkConnection() {
        if (!isEnabled()) {
            backoff.onSuccess(); // nothing to connect → keep the gate clear so enabling reconnects at once
            return;
        }
        if (isConnected()) {
            backoff.onSuccess();
            log.debug("WaveLink connected, sending ping.");
            ping();
            // Keep the controlled-app cache current even when no channelChanged event fires after the
            // initial channel load, so the set is persisted for the next startup's focus-volume race.
            syncAppCache();
        } else if (backoff.ready(System.currentTimeMillis())) {
            log.info("WaveLink not connected, connecting.");
            reconnect();
            // reconnect() is async; record an attempt and let the next tick clear the backoff once connected.
            backoff.onFailure(System.currentTimeMillis());
        }
    }

    @Override
    public void connectionClosed() {
        log.info("WaveLink connection closed.");
        fireChanged();
    }

    // Wave Link state (incl. mute) changed: notify observers (e.g. the mute-colour layer) to re-read state.
    @Override
    public void channelChanged(WaveLinkChannel channel) {
        log.debug("WaveLink channelChanged id={} name={} muted={}", channel.id(), channel.name(), channel.isMuted());
        syncAppCache();
        fireChanged();
    }

    @Override
    public void channelsChanged() {
        syncAppCache();
        fireChanged();
    }

    private void syncAppCache() {
        if (appCache != null) {
            appCache.syncFromChannels(getChannels().values());
        }
    }

    @Override
    public void mixChanged(WaveLinkMix mix) {
        fireChanged();
    }

    @Override
    public void mixesChanged() {
        fireChanged();
    }

    @Override
    public void outputDeviceChanged(WaveLinkOutputDevice outputDevice) {
        fireChanged();
    }

    @Override
    public void outputDevicesChanged() {
        fireChanged();
    }

    private void fireChanged() {
        if (changedEvent != null) {
            changedEvent.fire(new WaveLinkChangedEvent());
        }
        // Channel membership may have changed (an app added/removed). Re-pin the focused app's volume if
        // it is now controlled — debounced so a burst of level/mute pushes coalesces into one check.
        if (debouncer != null) {
            debouncer.debounce(ENFORCE_VOLUME_KEY, this::enforceFocusedControlledVolume, 250, TimeUnit.MILLISECONDS);
        }
    }

    private static final String ENFORCE_VOLUME_KEY = "wavelink-enforce-controlled-volume";

    /**
     * Returns the WaveLink channel ID that the current WaveLink-focused application is assigned to,
     * if any. This is determined by searching all known channels for the app that WaveLink currently
     * reports as focused ({@code lastFocusApp}).
     * <p>
     * Returns {@link Optional#empty()} when WaveLink is not connected, has no focused app, or the
     * focused app is not assigned to any channel.
     */
    public Optional<String> findChannelIdForFocusApp() {
        return findChannelIdForApp(getLastFocusApp());
    }

    /**
     * Whether Wave Link's focused app and a channel's app are the same. Matched on id <em>or</em> name
     * (case-insensitive): the focused-app push and the channel membership don't always carry the same
     * identifier for the same app (e.g. an exe path vs a display name), so an id-only compare misses
     * apps like a browser whose Wave Link name differs from its executable.
     */
    private static boolean appsMatch(WaveLinkApp a, WaveLinkApp b) {
        return (StringUtils.isNotBlank(a.id()) && StringUtils.equalsIgnoreCase(a.id(), b.id()))
                || (StringUtils.isNotBlank(a.name()) && StringUtils.equalsIgnoreCase(a.name(), b.name()));
    }

    @Override
    public void onError(Throwable t) {
        if (t instanceof CompletionException ce) {
            t = ce.getCause();
        }

        if (t instanceof ConnectException) {
            log.warn("Unable to connect to WaveLink");
        }
    }
}
