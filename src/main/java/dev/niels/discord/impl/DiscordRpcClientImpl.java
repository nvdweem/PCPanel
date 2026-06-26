package dev.niels.discord.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.niels.discord.DiscordRpcException;
import dev.niels.discord.IDiscordRpcClient;
import dev.niels.discord.IDiscordRpcListener;
import dev.niels.discord.model.DiscordUser;
import dev.niels.discord.model.DiscordVoiceSettings;
import dev.niels.discord.model.DiscordVoiceUser;
import lombok.extern.log4j.Log4j2;

/**
 * Pure-Java Discord local IPC (RPC) client. Owns the transport (a Windows pipe or Unix socket), a single
 * blocking read thread, and request/response correlation by {@code nonce}. The OAuth token exchange is
 * deliberately NOT here (it needs the client secret + persistence) — the integration layer drives the
 * {@link #authorize}/{@link #authenticate} handshake and supplies the access token.
 *
 * <p>Wire model: an 8-byte little-endian header (opcode, length) + a JSON body. Commands are opcode 1
 * with {@code {cmd,args,nonce}}; responses echo the {@code nonce} and carry {@code data} (or
 * {@code evt:"ERROR"}); server pushes are DISPATCH frames with an {@code evt} and no matching nonce.
 * Bodies are handled as Jackson trees (no per-message DTOs) to keep the native image free of extra
 * reflection registrations.
 */
@Log4j2
public abstract class DiscordRpcClientImpl implements IDiscordRpcClient {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final long REQUEST_TIMEOUT_MS = 10_000;
    private static final String[] VOICE_STATE_EVENTS = { "VOICE_STATE_CREATE", "VOICE_STATE_UPDATE", "VOICE_STATE_DELETE" };

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final List<IDiscordRpcListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DiscordVoiceUser> voiceUsers = new ConcurrentHashMap<>();

    protected volatile String clientId;
    private volatile DiscordIpcConnection connection;
    private volatile boolean authenticated;
    @Nullable private volatile DiscordUser selfUser;
    @Nullable private volatile String currentChannelId;
    private volatile DiscordVoiceSettings voiceSettings = DiscordVoiceSettings.EMPTY;
    @Nullable private CompletableFuture<DiscordUser> readyFuture;

    protected DiscordRpcClientImpl() {
    }

    public void setClientId(@Nullable String clientId) {
        this.clientId = clientId;
    }

    // ── connection lifecycle ────────────────────────────────────────────────────

    /** Opens the IPC endpoint, starts the read thread and sends the HANDSHAKE; completes with the READY user. */
    public synchronized CompletableFuture<DiscordUser> connect() {
        if (StringUtils.isBlank(clientId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("No Discord client id configured"));
        }
        disconnect();
        var conn = DiscordIpcConnections.open();
        if (conn == null) {
            return CompletableFuture.failedFuture(new IOException("No Discord IPC endpoint found (is Discord running?)"));
        }
        connection = conn;
        var future = new CompletableFuture<DiscordUser>();
        readyFuture = future;
        try {
            // Send the handshake BEFORE starting the read thread. A blocking read concurrent with the
            // very first write on the same pipe can delay the handshake past Discord's timeout —
            // RandomAccessFile shares one file pointer across read and write — which Discord answers with
            // a "Handshake timeout" close instead of processing it. Writing it first matches the
            // single-threaded ordering Discord accepts; later command writes can safely race the reads.
            conn.writeFrame(OP_HANDSHAKE, mapper.writeValueAsBytes(mapper.createObjectNode().put("v", 1).put("client_id", clientId)));
        } catch (IOException e) {
            connection = null;
            conn.close();
            future.completeExceptionally(e);
            return future;
        }
        var thread = new Thread(() -> readLoop(conn), "discord-ipc-read");
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    public synchronized void disconnect() {
        var conn = connection;
        connection = null;
        authenticated = false;
        voiceUsers.clear();
        currentChannelId = null;
        if (conn != null) {
            conn.close(); // the read thread sees isOpen()==false / a read error and exits, firing onConnectionDropped
        }
    }

    @Override
    public boolean isConnected() {
        var c = connection;
        return c != null && c.isOpen();
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated && isConnected();
    }

    /** Best-effort keep-alive; drops the connection on write failure so the next health-check reconnects. */
    public void ping() {
        var c = connection;
        if (c == null) {
            return;
        }
        try {
            c.writeFrame(OP_PING, "{}".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("Discord ping failed", e);
            disconnect();
        }
    }

    private void readLoop(DiscordIpcConnection conn) {
        try {
            while (conn.isOpen()) {
                dispatch(conn, conn.readFrame());
            }
        } catch (IOException e) {
            log.debug("Discord IPC read ended: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Discord IPC read loop error", e);
        } finally {
            onConnectionDropped(conn);
        }
    }

    private synchronized void onConnectionDropped(DiscordIpcConnection conn) {
        conn.close();
        if (connection == conn) { // only react if this thread still owns the live connection
            connection = null;
            authenticated = false;
            voiceUsers.clear();
            currentChannelId = null;
            failPending();
            var rf = readyFuture;
            if (rf != null && !rf.isDone()) {
                rf.completeExceptionally(new IOException("Disconnected before ready"));
            }
        }
        trigger(IDiscordRpcListener::connectionClosed);
    }

    private void failPending() {
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Discord IPC disconnected")));
        pending.clear();
    }

    // ── request / response ──────────────────────────────────────────────────────

    /** Sends a command ({@code {cmd,args,nonce}}) and completes with its {@code data} (or fails on an ERROR frame). */
    public CompletableFuture<JsonNode> send(String cmd, @Nullable ObjectNode args) {
        return request(cmd, null, args);
    }

    public CompletableFuture<JsonNode> subscribe(String evt, @Nullable ObjectNode args) {
        return request("SUBSCRIBE", evt, args);
    }

    public CompletableFuture<JsonNode> unsubscribe(String evt, @Nullable ObjectNode args) {
        return request("UNSUBSCRIBE", evt, args);
    }

    private CompletableFuture<JsonNode> request(String cmd, @Nullable String evt, @Nullable ObjectNode args) {
        var conn = connection;
        var future = new CompletableFuture<JsonNode>();
        if (conn == null || !conn.isOpen()) {
            future.completeExceptionally(new IOException("Discord IPC not connected"));
            return future;
        }
        var nonce = UUID.randomUUID().toString();
        var node = mapper.createObjectNode();
        node.put("cmd", cmd);
        if (evt != null) {
            node.put("evt", evt);
        }
        if (args != null) {
            node.set("args", args);
        }
        node.put("nonce", nonce);
        pending.put(nonce, future);
        future.orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).whenComplete((r, e) -> pending.remove(nonce));
        try {
            conn.writeFrame(OP_FRAME, mapper.writeValueAsBytes(node));
        } catch (IOException e) {
            pending.remove(nonce);
            future.completeExceptionally(e);
        }
        return future;
    }

    private void dispatch(DiscordIpcConnection conn, DiscordIpcConnection.Frame frame) throws IOException {
        switch (frame.opcode()) {
            case OP_FRAME -> handleFrame(mapper.readTree(frame.body()));
            case OP_PING -> conn.writeFrame(OP_PONG, frame.body());
            case OP_PONG -> { /* keep-alive ack */ }
            case OP_CLOSE -> {
                var node = mapper.readTree(frame.body());
                log.info("Discord IPC closed by server: {}", node);
                trigger(l -> l.onError(new DiscordRpcException(node)));
                conn.close(); // ends the read loop, which fires connectionClosed
            }
            default -> log.debug("Unhandled Discord opcode {}", frame.opcode());
        }
    }

    private void handleFrame(JsonNode node) {
        var nonce = text(node, "nonce");
        var evt = text(node, "evt");
        var data = node.get("data");
        if (nonce != null) {
            var future = pending.remove(nonce);
            if (future != null) {
                if ("ERROR".equals(evt)) {
                    future.completeExceptionally(new DiscordRpcException(data));
                } else {
                    future.complete(data);
                }
                return;
            }
        }
        handleEvent(evt, data);
    }

    private void handleEvent(@Nullable String evt, @Nullable JsonNode data) {
        if (evt == null) {
            return;
        }
        switch (evt) {
            case "READY" -> onReady(data);
            case "VOICE_SETTINGS_UPDATE" -> setVoiceSettings(parseVoiceSettings(data));
            case "VOICE_STATE_CREATE", "VOICE_STATE_UPDATE" -> upsertVoiceUser(parseVoiceState(data));
            case "VOICE_STATE_DELETE" -> removeVoiceUser(data);
            case "VOICE_CHANNEL_SELECT" -> onVoiceChannelSelect(data);
            case "ERROR" -> {
                log.warn("Discord RPC error event: {}", data);
                trigger(l -> l.onError(new DiscordRpcException(data)));
            }
            default -> log.debug("Unhandled Discord event {}", evt);
        }
    }

    private void onReady(@Nullable JsonNode data) {
        selfUser = parseUser(data == null ? null : data.get("user"));
        var rf = readyFuture;
        if (rf != null) {
            rf.complete(selfUser);
        }
        trigger(l -> l.ready(selfUser));
    }

    // ── authorization (driven by the integration layer) ─────────────────────────

    /** Sends AUTHORIZE — Discord shows a consent popup — and completes with the returned OAuth code. */
    public CompletableFuture<String> authorize(List<String> scopes) {
        var args = mapper.createObjectNode();
        args.put("client_id", clientId);
        var arr = args.putArray("scopes");
        scopes.forEach(arr::add);
        return send("AUTHORIZE", args).thenApply(data -> text(data, "code"));
    }

    /** Sends AUTHENTICATE with a previously-obtained access token; marks the connection authenticated on success. */
    public CompletableFuture<DiscordUser> authenticate(String accessToken) {
        return send("AUTHENTICATE", mapper.createObjectNode().put("access_token", accessToken))
                .thenApply(data -> {
                    authenticated = true;
                    selfUser = parseUser(data == null ? null : data.get("user"));
                    trigger(l -> l.authenticated(selfUser));
                    return selfUser;
                });
    }

    /** After authentication: load self voice settings + current channel, and subscribe to the relevant pushes. */
    public CompletableFuture<Void> initVoiceState() {
        var settings = send("GET_VOICE_SETTINGS", null).thenAccept(d -> setVoiceSettings(parseVoiceSettings(d)));
        subscribe("VOICE_SETTINGS_UPDATE", null);
        subscribe("VOICE_CHANNEL_SELECT", null);
        var channel = refreshSelectedVoiceChannel();
        return CompletableFuture.allOf(settings, channel);
    }

    // ── voice control ───────────────────────────────────────────────────────────

    @Override
    public void setSelfMute(boolean mute) {
        send("SET_VOICE_SETTINGS", mapper.createObjectNode().put("mute", mute))
                .thenAccept(d -> setVoiceSettings(parseVoiceSettings(d))).exceptionally(logVoiceError());
    }

    @Override
    public void setSelfDeaf(boolean deaf) {
        send("SET_VOICE_SETTINGS", mapper.createObjectNode().put("deaf", deaf))
                .thenAccept(d -> setVoiceSettings(parseVoiceSettings(d))).exceptionally(logVoiceError());
    }

    @Override
    public void setInputVolume(float volume) {
        var node = mapper.createObjectNode();
        node.set("input", mapper.createObjectNode().put("volume", (int) clamp(volume, 0, 100)));
        send("SET_VOICE_SETTINGS", node)
                .thenAccept(d -> setVoiceSettings(parseVoiceSettings(d))).exceptionally(logVoiceError());
    }

    @Override
    public void setOutputVolume(float volume) {
        var node = mapper.createObjectNode();
        node.set("output", mapper.createObjectNode().put("volume", (int) clamp(volume, 0, 200)));
        send("SET_VOICE_SETTINGS", node)
                .thenAccept(d -> setVoiceSettings(parseVoiceSettings(d))).exceptionally(logVoiceError());
    }

    @Override
    public void setUserVolume(String userId, float volume) {
        send("SET_USER_VOICE_SETTINGS", mapper.createObjectNode().put("user_id", userId).put("volume", (int) clamp(volume, 0, 200)))
                .thenAccept(this::mergeUserVoice).exceptionally(logVoiceError());
    }

    @Override
    public void setUserMute(String userId, boolean mute) {
        send("SET_USER_VOICE_SETTINGS", mapper.createObjectNode().put("user_id", userId).put("mute", mute))
                .thenAccept(this::mergeUserVoice).exceptionally(logVoiceError());
    }

    // ── voice-channel membership tracking ───────────────────────────────────────

    public CompletableFuture<Void> refreshSelectedVoiceChannel() {
        return send("GET_SELECTED_VOICE_CHANNEL", null)
                .thenAccept(this::onSelectedVoiceChannel)
                .exceptionally(e -> {
                    log.debug("GET_SELECTED_VOICE_CHANNEL failed", e);
                    return null;
                });
    }

    private void onSelectedVoiceChannel(@Nullable JsonNode data) {
        var newChannel = text(data, "id");
        var old = currentChannelId;
        if (old != null && !old.equals(newChannel)) {
            unsubscribeChannel(old);
        }
        voiceUsers.clear();
        if (data != null && data.has("voice_states")) {
            for (var vs : data.get("voice_states")) {
                var u = parseVoiceState(vs);
                if (u != null && u.id() != null) {
                    voiceUsers.put(u.id(), u);
                }
            }
        }
        currentChannelId = newChannel;
        if (newChannel != null) {
            subscribeChannel(newChannel);
        }
        fireVoiceUsers();
    }

    private void onVoiceChannelSelect(@Nullable JsonNode data) {
        var channelId = text(data, "channel_id");
        trigger(l -> l.voiceChannelSelected(channelId));
        refreshSelectedVoiceChannel();
    }

    private void subscribeChannel(String channelId) {
        for (var evt : VOICE_STATE_EVENTS) {
            subscribe(evt, mapper.createObjectNode().put("channel_id", channelId));
        }
    }

    private void unsubscribeChannel(String channelId) {
        for (var evt : VOICE_STATE_EVENTS) {
            unsubscribe(evt, mapper.createObjectNode().put("channel_id", channelId));
        }
    }

    private void upsertVoiceUser(@Nullable DiscordVoiceUser u) {
        if (u == null || u.id() == null) {
            return;
        }
        voiceUsers.put(u.id(), u);
        fireVoiceUsers();
    }

    private void removeVoiceUser(@Nullable JsonNode data) {
        var id = text(data == null ? null : data.get("user"), "id");
        if (id != null && voiceUsers.remove(id) != null) {
            fireVoiceUsers();
        }
    }

    private void mergeUserVoice(@Nullable JsonNode data) {
        var id = text(data, "user_id");
        if (id == null) {
            return;
        }
        var existing = voiceUsers.get(id);
        if (existing == null) {
            return; // a user we don't currently track (not in our channel) — nothing to refresh
        }
        var volume = data.has("volume") ? data.get("volume").asInt(existing.volume()) : existing.volume();
        var mute = data.has("mute") ? data.get("mute").asBoolean(existing.mute()) : existing.mute();
        voiceUsers.put(id, new DiscordVoiceUser(existing.id(), existing.username(), existing.globalName(), existing.nick(), volume, mute));
        fireVoiceUsers();
    }

    private void fireVoiceUsers() {
        var snapshot = List.copyOf(voiceUsers.values());
        trigger(l -> l.voiceUsersChanged(snapshot));
    }

    private void setVoiceSettings(DiscordVoiceSettings s) {
        voiceSettings = s;
        trigger(l -> l.voiceSettingsUpdated(s));
    }

    // ── state accessors ─────────────────────────────────────────────────────────

    @Override
    public Collection<DiscordVoiceUser> getVoiceUsers() {
        return voiceUsers.values();
    }

    @Override
    public DiscordVoiceSettings getVoiceSettings() {
        return voiceSettings;
    }

    @Override
    @Nullable
    public DiscordUser getSelfUser() {
        return selfUser;
    }

    @Nullable
    public String getCurrentChannelId() {
        return currentChannelId;
    }

    // ── listeners ───────────────────────────────────────────────────────────────

    @Override
    public void addListener(IDiscordRpcListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(IDiscordRpcListener listener) {
        listeners.remove(listener);
    }

    protected void trigger(Consumer<IDiscordRpcListener> event) {
        listeners.forEach(event);
    }

    // ── parsing helpers ─────────────────────────────────────────────────────────

    @Nullable
    private DiscordUser parseUser(@Nullable JsonNode u) {
        if (u == null || u.isNull()) {
            return null;
        }
        return new DiscordUser(text(u, "id"), text(u, "username"), text(u, "global_name"));
    }

    @Nullable
    private DiscordVoiceUser parseVoiceState(@Nullable JsonNode vs) {
        if (vs == null) {
            return null;
        }
        var user = vs.get("user");
        var volume = vs.has("volume") ? vs.get("volume").asInt(100) : 100;
        var mute = vs.path("mute").asBoolean(false); // local (client-side) mute set via SET_USER_VOICE_SETTINGS
        return new DiscordVoiceUser(text(user, "id"), text(user, "username"), text(user, "global_name"), text(vs, "nick"), volume, mute);
    }

    private DiscordVoiceSettings parseVoiceSettings(@Nullable JsonNode d) {
        if (d == null) {
            return DiscordVoiceSettings.EMPTY;
        }
        var input = volumeOf(d.path("input"));
        var output = volumeOf(d.path("output"));
        return new DiscordVoiceSettings(d.path("mute").asBoolean(false), d.path("deaf").asBoolean(false), input, output);
    }

    @Nullable
    private static Integer volumeOf(JsonNode node) {
        var vol = node.path("volume");
        return vol.isMissingNode() || vol.isNull() ? null : (int) vol.asDouble();
    }

    @Nullable
    private static String text(@Nullable JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        var v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private Function<Throwable, Void> logVoiceError() {
        return e -> {
            log.debug("Discord voice command failed", e);
            return null;
        };
    }
}
