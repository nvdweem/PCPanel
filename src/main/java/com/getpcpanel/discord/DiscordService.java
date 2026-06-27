package com.getpcpanel.discord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.profile.dto.DiscordAuth;
import com.getpcpanel.profile.dto.DiscordSeenUser;
import com.getpcpanel.profile.dto.DiscordSettings;
import com.getpcpanel.util.Debouncer;
import com.getpcpanel.util.ReconnectBackoff;

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
 *
 * <p>Note: starting/stopping screen share (Go Live) is intentionally absent — Discord exposes no RPC
 * command for it, so it cannot be controlled this way (a Keystroke action bound to a Discord keybind is
 * the only workaround).
 */
@Log4j2
@ApplicationScoped
public class DiscordService extends DiscordRpcClient implements IDiscordRpcListener {
    private static final List<String> SCOPES = List.of("rpc", "rpc.voice.read", "rpc.voice.write");
    private static final String SEEN_USERS_KEY = "discord-seen-users";
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
                    return authorize(SCOPES);
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
                .thenCompose(self -> initVoiceState().thenApply(v -> self))
                .whenComplete((self, e) -> {
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
        learnSeenUsers(users);
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

    private void learnSeenUsers(Collection<DiscordVoiceUser> users) {
        if (saveService == null || users.isEmpty()) {
            return;
        }
        var current = new LinkedHashMap<String, DiscordSeenUser>();
        for (var u : saveService.get().getDiscordSeenUsers()) {
            current.put(u.id(), u);
        }
        var changed = false;
        for (var u : users) {
            if (u.id() == null || StringUtils.isBlank(u.username()) || isSelf(u.id())) {
                continue; // never list yourself as a targetable "seen user" — the per-user commands can't target you
            }
            var entry = new DiscordSeenUser(u.id(), u.username(), u.displayName());
            if (!entry.equals(current.get(u.id()))) {
                current.put(u.id(), entry);
                changed = true;
            }
        }
        if (changed) {
            saveService.get().setDiscordSeenUsers(new ArrayList<>(current.values()));
            // Persist off the IPC read thread, coalescing a burst of voice-state pushes into one write.
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

    /** Set own mic volume from a 0..1 dial fraction (mapped to Discord's 0-100 input range). */
    public void applyInputVolume(float fraction) {
        setInputVolume(fraction * 100f);
    }

    /** Set own output volume from a 0..1 dial fraction (mapped to Discord's 0-200 output range). */
    public void applyOutputVolume(float fraction) {
        setOutputVolume(fraction * 200f);
    }

    /** Set how loudly you hear {@code username} from a 0..1 dial fraction (mapped to Discord's 0-200 range). */
    public boolean applyUserVolume(String username, float fraction) {
        var id = resolveUserId(username);
        if (id == null) {
            log.warn("Discord user '{}' not found (not in your voice channel / not seen yet)", username);
            return false;
        }
        if (isSelf(id)) {
            log.warn("Discord 'user volume' can't target yourself — that's how loud you hear someone else. Use 'Mic Volume' (your input) or 'Output Volume' (how loud you hear others) instead.");
            return false;
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
        var current = getVoiceUsers().stream().filter(u -> id.equals(u.id())).findFirst().map(DiscordVoiceUser::mute).orElse(false);
        setUserMute(id, type.convert(current));
        return true;
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
