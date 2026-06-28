package com.getpcpanel.integration.discord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.integration.discord.dto.DiscordAuth;
import com.getpcpanel.integration.discord.dto.DiscordSeenUser;
import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.util.concurrent.Debouncer;
import com.getpcpanel.util.concurrent.ReconnectBackoff;

import dev.niels.discord.DiscordRpcClient;
import dev.niels.discord.DiscordRpcException;
import dev.niels.discord.IDiscordRpcListener;
import dev.niels.discord.model.DiscordUser;
import dev.niels.discord.model.DiscordVoiceChannel;
import dev.niels.discord.model.DiscordVoiceUser;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Wires the standalone {@link DiscordRpcClient} into the app: reads credentials from {@link SaveService},
 * keeps the IPC connection alive on a schedule (with {@link ReconnectBackoff}), drives the OAuth
 * authorize/authenticate handshake, refreshes and persists the token, and tracks the current voice
 * channel's members plus a persistent "seen users" roster. State changes fire a {@link DiscordChangedEvent}
 * so the UI re-reads live data.
 */
@Log4j2
@ApplicationScoped
public class DiscordService extends DiscordRpcClient implements IDiscordRpcListener {
    // The rpc.* scopes are owner-gated, so a self-registered app gets them without approval. relationships.read
    // (for the friends list) is NOT owner-gated: since 2026-01-21 it needs the app to accept the Discord Social
    // SDK Terms (https://discord.com/developers/applications/select/social-sdk/getting-started) — until then
    // Discord rejects the AUTHORIZE with invalid_scope. So we REQUEST it but fall back to CORE_SCOPES if it's
    // refused, so a missing acceptance never breaks the (critical) voice authorization.
    private static final List<String> CORE_SCOPES = List.of("rpc", "rpc.voice.read", "rpc.voice.write", "rpc.screenshare.write", "rpc.video.write");
    private static final List<String> SCOPES = List.of("rpc", "rpc.voice.read", "rpc.voice.write", "rpc.screenshare.write", "rpc.video.write", "relationships.read");
    private static final String SEEN_USERS_KEY = "discord-seen-users";
    /** Cap on the persisted "people you've targeted" roster, so it can't grow without bound. */
    private static final int MAX_SEEN_USERS = 100;
    /** Refresh the access token this far before its stated expiry. */
    private static final long TOKEN_REFRESH_MARGIN_MS = 60_000;

    private final SaveService saveService;
    /** Spaces out reconnect attempts when Discord is down (base = the scheduled interval, capped at 5 min). */
    private final ReconnectBackoff backoff = new ReconnectBackoff(10_000, 300_000);
    private final AtomicBoolean authInProgress = new AtomicBoolean(false);
    private boolean wasEnabled;
    @Nullable private String lastClientId;
    /** Why the last authorize attempt failed (surfaced to the UI so the user isn't left guessing). */
    @Nullable private volatile String lastAuthError;
    /** The user's Discord friends (empty unless relationships.read was granted), pickable as command targets. */
    private volatile List<DiscordSeenUser> friends = List.of();

    @Inject Event<DiscordChangedEvent> changedEvent;
    @Inject Debouncer debouncer;

    DiscordService() {
        super();
        saveService = null;
    }

    @Inject
    public DiscordService(SaveService saveService) {
        super();
        this.saveService = saveService;
        addListener(this);
        var cfg = settings();
        lastClientId = cfg.clientId();
        setClientId(cfg.clientId());
        wasEnabled = isEnabled();
    }

    // ── config ──────────────────────────────────────────────────────────────────

    public DiscordSettings settings() {
        return saveService == null ? DiscordSettings.DEFAULT : saveService.get().getDiscord();
    }

    /** Enabled and minimally configured (a client id + secret are required for the voice-control auth flow). */
    public boolean isEnabled() {
        var cfg = settings();
        return cfg.enabled() && StringUtils.isNoneBlank(cfg.clientId(), cfg.clientSecret());
    }

    public void settingsChanged(@Observes SaveEvent event) {
        if (saveService == null) {
            return;
        }
        var cfg = settings();
        var enabled = isEnabled();
        var clientIdChanged = !StringUtils.equals(lastClientId, cfg.clientId());
        setClientId(cfg.clientId());
        if (!enabled) {
            if (wasEnabled || isConnected()) {
                disconnect();
            }
        } else if (!wasEnabled || clientIdChanged) {
            // Just enabled, or the app id changed — (re)connect; the ready callback authenticates.
            disconnect();
            connectAndAuthenticate();
        }
        wasEnabled = enabled;
        lastClientId = cfg.clientId();
    }

    @Scheduled(every = "10s")
    public void checkConnection() {
        if (saveService == null || !isEnabled()) {
            backoff.onSuccess(); // nothing to connect → keep the gate clear so enabling reconnects at once
            return;
        }
        if (isConnected()) {
            backoff.onSuccess();
            if (isAuthenticated()) {
                ping();
            } else {
                authenticateWithStoredToken(); // connected but not yet authenticated (e.g. token just stored)
            }
        } else if (backoff.ready(System.currentTimeMillis())) {
            log.info("Discord not connected, connecting.");
            connectAndAuthenticate();
            backoff.onFailure(System.currentTimeMillis());
        }
    }

    private void connectAndAuthenticate() {
        connect().exceptionally(e -> {
            onError(e);
            return null;
        });
        // authentication is kicked off from the ready() callback once the handshake completes
    }

    // ── authorization ───────────────────────────────────────────────────────────

    /**
     * User-triggered "Connect &amp; Authorize": connect if needed, run AUTHORIZE (Discord shows a consent
     * popup), exchange the returned code for a token, persist it, AUTHENTICATE and load voice state.
     */
    public CompletableFuture<DiscordUser> authorizeInteractive() {
        var cfg = settings();
        if (StringUtils.isAnyBlank(cfg.clientId(), cfg.clientSecret())) {
            lastAuthError = "Set the Discord Client ID and Client Secret first";
            return CompletableFuture.failedFuture(new IllegalStateException(lastAuthError));
        }
        // Hold the same gate the health-check honours, so a scheduled authenticateWithStoredToken can't fire
        // AUTHENTICATE on the connection mid-authorize ("Already authenticating"), and a double-click is a no-op.
        if (!authInProgress.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord authorization already in progress"));
        }
        setClientId(cfg.clientId());
        lastAuthError = null;
        log.info("Discord authorize: connecting to Discord…");
        // Always (re)connect with a fresh handshake rather than reusing the live connection. Discord renders
        // the consent popup for an AUTHORIZE that directly follows a handshake; sent down a long-idle
        // connection (whose Windows pipe can still read as open after Discord dropped it) the AUTHORIZE is
        // silently swallowed — no popup, then a 120s timeout. A fresh connect also keeps the chain off the
        // HTTP request thread (the ready future isn't pre-completed), so POST /authorize returns at once
        // instead of blocking on the pipe write.
        return connect()
                .thenCompose(x -> {
                    log.info("Discord authorize: requesting consent — APPROVE THE POPUP INSIDE DISCORD…");
                    return authorizeWithFriendsFallback();
                })
                .thenCompose(code -> {
                    log.info("Discord authorize: exchanging code for an access token…");
                    return DiscordOAuth.exchangeCode(cfg.clientId(), cfg.clientSecret(), cfg.redirectUri(), code);
                })
                .thenApply(this::storeToken)
                .thenCompose(token -> {
                    log.info("Discord authorize: reconnecting to authenticate on a clean socket…");
                    // Discord stops servicing the pipe that handled AUTHORIZE (writes block, no reply), so
                    // AUTHENTICATE must run on a fresh handshake — not the authorize connection.
                    return connect().thenCompose(x -> authenticate(token));
                })
                .thenCompose(self -> initVoiceState().thenCompose(v -> loadFriends()).thenApply(v -> self))
                .whenComplete((self, e) -> {
                    authInProgress.set(false);
                    if (e != null) {
                        lastAuthError = rootMessage(e);
                        log.warn("Discord authorization failed: {}", lastAuthError, e);
                    } else {
                        lastAuthError = null;
                        log.info("Discord authorized as {}", self == null ? "?" : self.displayName());
                    }
                    fireChanged();
                });
    }

    /** AUTHORIZE with the friends scope, retrying voice-only on a fresh connection if Discord refuses it. */
    private CompletableFuture<String> authorizeWithFriendsFallback() {
        return authorize(SCOPES).handle((code, err) -> {
            if (err == null) {
                return CompletableFuture.completedFuture(code);
            }
            if (isInvalidScope(err)) {
                log.info("Discord refused relationships.read (accept the Social SDK Terms for your app to enable the friends list) — re-authorizing voice-only.");
                return connect().thenCompose(x -> authorize(CORE_SCOPES));
            }
            return CompletableFuture.<String>failedFuture(err);
        }).thenCompose(f -> f);
    }

    private static boolean isInvalidScope(Throwable e) {
        var msg = rootMessage(e);
        return msg != null && msg.contains("invalid_scope");
    }

    /**
     * Removes the stored authorization and drops the live connection — the user can then re-authorize from a
     * clean slate (the client id/secret are kept). Fixes the "have to click authorize several times" flakiness:
     * re-authorizing on top of a live, already-authenticated session races the health-check and Discord's own
     * "already authenticating" guard.
     */
    public void signOut() {
        if (saveService != null) {
            saveService.get().setDiscordAuth(null);
            saveService.save();
        }
        lastAuthError = null;
        disconnect(); // clears authenticated state + voice users; the health-check stays idle with no stored token
        friends = List.of();
        fireChanged();
    }

    private void authenticateWithStoredToken() {
        if (saveService == null || !authInProgress.compareAndSet(false, true)) {
            return;
        }
        var auth = saveService.get().getDiscordAuth();
        if (auth == null || !auth.hasToken()) {
            authInProgress.set(false);
            return; // no stored token → wait for the user to authorize
        }
        ensureFreshToken(auth)
                .thenCompose(this::authenticate)
                .thenCompose(self -> initVoiceState())
                .thenCompose(v -> loadFriends())
                .whenComplete((v, e) -> {
                    authInProgress.set(false);
                    if (e != null) {
                        clearAuthIfRejected(e);
                        if (isConnected() && !isAuthenticated()) {
                            // The connection accepted the AUTHENTICATE write but Discord never answered — a
                            // half-dead pipe (it stops servicing the socket after an AUTHORIZE/redirect). Drop it
                            // so the next health-check reconnects fresh and re-authenticates on a live socket,
                            // rather than retrying forever against a pipe Discord has stopped reading.
                            log.warn("Discord authentication failed; dropping the stale connection to retry fresh", e);
                            disconnect();
                        } else {
                            log.warn("Discord authentication with stored token failed; re-authorization may be needed", e);
                        }
                    }
                    fireChanged();
                });
    }

    private CompletableFuture<String> ensureFreshToken(DiscordAuth auth) {
        var fresh = auth.expiresAtEpochMs() == null || auth.expiresAtEpochMs() > System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MS;
        if (fresh || StringUtils.isBlank(auth.refreshToken())) {
            return CompletableFuture.completedFuture(auth.accessToken());
        }
        var cfg = settings();
        return DiscordOAuth.refresh(cfg.clientId(), cfg.clientSecret(), auth.refreshToken()).thenApply(this::storeToken);
    }

    private String storeToken(DiscordOAuth.TokenResponse token) {
        if (saveService == null) {
            return token.accessToken();
        }
        var self = getSelfUser();
        var auth = new DiscordAuth(
                token.accessToken(),
                token.refreshToken(),
                token.expiresInSeconds() > 0 ? System.currentTimeMillis() + token.expiresInSeconds() * 1000 : null,
                token.scope(),
                self == null ? null : self.id(),
                self == null ? null : self.displayName());
        saveService.get().setDiscordAuth(auth);
        saveService.save();
        return token.accessToken();
    }

    /** Clears the stored token only when Discord actually rejected it (so a transient network error doesn't force re-auth). */
    private void clearAuthIfRejected(Throwable e) {
        var cause = e instanceof CompletionException ce ? ce.getCause() : e;
        if (cause instanceof DiscordRpcException && saveService != null) {
            saveService.get().setDiscordAuth(null);
            saveService.save();
        }
    }

    // ── listener callbacks (IPC read thread) ─────────────────────────────────────

    @Override
    public void ready(DiscordUser self) {
        log.info("Discord IPC ready: {}", self == null ? "?" : self.displayName());
        authenticateWithStoredToken();
        fireChanged();
    }

    @Override
    public void authenticated(DiscordUser self) {
        log.info("Discord authenticated as {}", self == null ? "?" : self.displayName());
        fireChanged();
    }

    @Override
    public void connectionClosed() {
        log.info("Discord connection closed.");
        fireChanged();
    }

    @Override
    public void voiceSettingsUpdated(dev.niels.discord.model.DiscordVoiceSettings settings) {
        fireChanged();
    }

    @Override
    public void voiceChannelSelected(String channelId) {
        fireChanged();
    }

    @Override
    public void voiceUsersChanged(List<DiscordVoiceUser> users) {
        fireChanged();
    }

    @Override
    public void onError(Throwable t) {
        var cause = t instanceof CompletionException ce ? ce.getCause() : t;
        if (cause instanceof java.io.IOException) {
            log.debug("Discord not reachable: {}", cause.getMessage());
        } else {
            log.warn("Discord RPC error", cause);
        }
    }

    private void fireChanged() {
        if (changedEvent != null) {
            changedEvent.fire(new DiscordChangedEvent());
        }
    }

    // ── seen-users roster ────────────────────────────────────────────────────────

    /**
     * Persists a single user you've actually targeted with a command, so their username stays pickable when
     * they're offline. Deliberately NOT every member of every channel you join — that would bloat the roster
     * with thousands of strangers from busy servers. Friends come from {@link #loadFriends()} separately and
     * are never persisted here. Capped (LRU: least-recently-targeted drops first) as a safety net.
     */
    private void rememberSeenUser(@Nullable DiscordVoiceUser u) {
        if (saveService == null || u == null || u.id() == null || StringUtils.isBlank(u.username()) || isSelf(u.id())) {
            return;
        }
        var current = new LinkedHashMap<String, DiscordSeenUser>();
        for (var s : saveService.get().getDiscordSeenUsers()) {
            current.put(s.id(), s);
        }
        var entry = new DiscordSeenUser(u.id(), u.username(), u.displayName());
        var previous = current.remove(u.id()); // remove+put = move to most-recent end
        current.put(u.id(), entry);
        var changed = !entry.equals(previous);
        while (current.size() > MAX_SEEN_USERS) { // trim the oldest (also self-heals any pre-existing bloat)
            current.remove(current.keySet().iterator().next());
            changed = true;
        }
        if (changed) {
            saveService.get().setDiscordSeenUsers(new ArrayList<>(current.values()));
            // Persist off the IPC read thread, coalescing a burst into one write.
            if (debouncer != null) {
                debouncer.debounce(SEEN_USERS_KEY, saveService::save, 2, TimeUnit.SECONDS);
            } else {
                saveService.save();
            }
        }
    }

    public List<DiscordSeenUser> getSeenUsers() {
        return saveService == null ? List.of() : saveService.get().getDiscordSeenUsers();
    }

    /** Loads the friend list (best-effort — needs the relationships.read scope; a failure just leaves it empty). */
    private CompletableFuture<Void> loadFriends() {
        return getRelationships()
                .thenAccept(list -> {
                    friends = list.stream()
                            .filter(u -> !isSelf(u.id()))
                            .map(u -> new DiscordSeenUser(u.id(), u.username(), u.displayName()))
                            .toList();
                    fireChanged();
                })
                .exceptionally(e -> {
                    log.debug("Discord friend list unavailable (relationships.read not granted?): {}", rootMessage(e));
                    return null;
                });
    }

    /** The user's friends, targetable by username before any shared call. */
    public List<DiscordSeenUser> getFriends() {
        return friends;
    }

    /** Whether a usable OAuth token is stored (the user has authorized at least once), regardless of the live connection. */
    public boolean hasStoredAuthorization() {
        if (saveService == null) {
            return false;
        }
        var auth = saveService.get().getDiscordAuth();
        return auth != null && auth.hasToken();
    }

    /** Why the last authorize attempt failed (null once authorized or never attempted). */
    @Nullable
    public String getLastAuthError() {
        return lastAuthError;
    }

    private static String rootMessage(Throwable e) {
        var cause = e;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** Best display name for status: the live authenticated user, else the name stored at authorization time. */
    @Nullable
    public String getStatusUserName() {
        var self = getSelfUser();
        if (self != null) {
            return self.displayName();
        }
        if (saveService != null) {
            var auth = saveService.get().getDiscordAuth();
            if (auth != null) {
                return auth.userName();
            }
        }
        return null;
    }

    // ── command-facing operations ────────────────────────────────────────────────

    public void toggleSelfMute(MuteType type) {
        setSelfMute(type.convert(getVoiceSettings().mute()));
    }

    public void toggleSelfDeafen(MuteType type) {
        setSelfDeaf(type.convert(getVoiceSettings().deaf()));
    }

    /** Set own mic volume from a 0..1 dial fraction (mapped to Discord's 0-100 input range), optionally self-unmuting. */
    public void applyInputVolume(float fraction, boolean unmute) {
        // Clear mute as its OWN frame, sent BEFORE the volume frame: Discord drops a mute/deaf change that
        // follows a volume change in a separate frame, and ignores a `mute` field folded into the volume
        // frame itself. Only when actually muted, so dial ticks don't spam it.
        if (unmute && getVoiceSettings().mute()) {
            setSelfMute(false);
        }
        setInputVolume(fraction * 100f);
    }

    /** Set own output volume from a 0..1 dial fraction (mapped to Discord's 0-200 output range), optionally self-undeafening. */
    public void applyOutputVolume(float fraction, boolean undeafen) {
        if (undeafen && getVoiceSettings().deaf()) {
            setSelfDeaf(false);
        }
        setOutputVolume(fraction * 200f);
    }

    /** Set how loudly you hear {@code username} from a 0..1 dial fraction (mapped to Discord's 0-200 range). */
    public boolean applyUserVolume(String username, float fraction) {
        return applyUserVolume(username, fraction, false);
    }

    /** As {@link #applyUserVolume(String, float)}, additionally locally-unmuting the user when {@code unmute}. */
    public boolean applyUserVolume(String username, float fraction, boolean unmute) {
        var id = resolveUserId(username);
        if (id == null) {
            log.warn("Discord user '{}' not found (not in your voice channel / not seen yet)", username);
            return false;
        }
        if (isSelf(id)) {
            log.warn("Discord 'user volume' can't target yourself — that's how loud you hear someone else. Use the mic or output volume target instead.");
            return false;
        }
        rememberTargeted(id);
        // Clear the local mute first (before the volume frame) for the same reason as self-volume, and only
        // when actually muted so a moving dial doesn't re-send unmute every tick.
        if (unmute && isUserMuted(id)) {
            setUserMute(id, false);
        }
        setUserVolume(id, fraction * 200f);
        return true;
    }

    public boolean toggleUserMute(String username, MuteType type) {
        var id = resolveUserId(username);
        if (id == null) {
            log.warn("Discord user '{}' not found (not in your voice channel / not seen yet)", username);
            return false;
        }
        if (isSelf(id)) {
            // Discord rejects SET_USER_VOICE_SETTINGS on your own id (error 4010); "mute yourself" just means self-mute.
            log.info("Discord 'user mute' targeted yourself — muting self instead. Bind the 'Self Mute' command to do this directly.");
            toggleSelfMute(type);
            return true;
        }
        rememberTargeted(id);
        var current = getVoiceUsers().stream().filter(u -> id.equals(u.id())).findFirst().map(DiscordVoiceUser::mute).orElse(false);
        setUserMute(id, type.convert(current));
        return true;
    }

    /** Persist a user you've just targeted (if they're a live channel member) so they stay pickable offline. */
    private void rememberTargeted(String id) {
        getVoiceUsers().stream().filter(u -> id.equals(u.id())).findFirst().ifPresent(this::rememberSeenUser);
    }

    private boolean isUserMuted(String userId) {
        return getVoiceUsers().stream().filter(u -> userId.equals(u.id())).findFirst().map(DiscordVoiceUser::mute).orElse(false);
    }

    /** Whether {@code userId} is the locally authenticated user (self-targeting the per-user voice commands is invalid). */
    private boolean isSelf(@Nullable String userId) {
        var self = getSelfUser();
        return self != null && StringUtils.equals(userId, self.id());
    }

    /** Join (connect to) a voice channel by id. */
    public void joinVoice(String channelId) {
        selectVoiceChannel(channelId, true).exceptionally(e -> {
            log.warn("Discord join voice failed", e);
            return null;
        });
    }

    /** Leave the current voice channel. */
    public void leaveVoice() {
        selectVoiceChannel(null, false).exceptionally(e -> {
            log.warn("Discord leave voice failed", e);
            return null;
        });
    }

    /** Voice channels across the user's guilds, for the join-command picker (empty until authenticated). */
    public CompletableFuture<List<DiscordVoiceChannel>> listVoiceChannels() {
        return isAuthenticated() ? getVoiceChannels() : CompletableFuture.completedFuture(List.of());
    }

    /** Resolves a stored/configured username to a current Discord user id: live channel members first, then the seen roster. */
    @Nullable
    public String resolveUserId(@Nullable String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        for (var u : getVoiceUsers()) {
            if (StringUtils.equalsIgnoreCase(u.username(), username)) {
                return u.id();
            }
        }
        for (var u : getSeenUsers()) {
            if (StringUtils.equalsIgnoreCase(u.username(), username)) {
                return u.id();
            }
        }
        return null;
    }
}
