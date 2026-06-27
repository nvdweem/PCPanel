package dev.niels.discord.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
 *
 * <p>Threading: the Windows pipe is a {@code RandomAccessFile} whose reads and closes are blocking and
 * <em>not</em> interruptible — a {@code close()} stalls until a pending {@code readFully} returns, and a
 * write to a half-dead pipe blocks until its OS buffer drains. So every write goes through the
 * connection's own single writer thread, and closing is handed to a throwaway thread; a caller (HTTP
 * request, scheduler tick, command dispatch, or the read thread) is therefore never the one that blocks.
 * A {@link Channel} bundles a transport with its writer so a reconnect swaps both atomically.
 */
@Log4j2
public abstract class DiscordRpcClientImpl implements IDiscordRpcClient {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final long REQUEST_TIMEOUT_MS = 10_000;
    /** AUTHORIZE blocks on the user clicking the consent popup inside Discord, so it gets a long window. */
    private static final long AUTHORIZE_TIMEOUT_MS = 120_000;
    /** If Discord never answers the handshake with READY, fail fast rather than hang the connect future. */
    private static final long HANDSHAKE_TIMEOUT_MS = 15_000;
    private static final String[] VOICE_STATE_EVENTS = { "VOICE_STATE_CREATE", "VOICE_STATE_UPDATE", "VOICE_STATE_DELETE" };

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final List<IDiscordRpcListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DiscordVoiceUser> voiceUsers = new ConcurrentHashMap<>();

    protected volatile String clientId;
    @Nullable private volatile Channel channel;
    private volatile boolean authenticated;
    @Nullable private volatile DiscordUser selfUser;
    @Nullable private volatile String currentChannelId;
    private volatile DiscordVoiceSettings voiceSettings = DiscordVoiceSettings.EMPTY;
    @Nullable private CompletableFuture<DiscordUser> readyFuture;

    /**
     * A live transport plus the dedicated single-thread writer that owns every write to it. Bundling the
     * two means a reconnect swaps them together (a write can never target a different connection's writer)
     * and that a connection abandoned because its blocking write/close never returned takes its stuck
     * threads down with it — bounded to one writer + one closer per disconnect, all daemon threads.
     */
    private record Channel(DiscordIpcConnection conn, ExecutorService writer) {
    }

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
        var writer = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "discord-ipc-write");
            t.setDaemon(true);
            return t;
        });
        var ch = new Channel(conn, writer);
        channel = ch;
        var future = new CompletableFuture<DiscordUser>();
        readyFuture = future;
        // Fail fast (and drop the dead connection) if Discord never sends READY, so the authorize/connect
        // chain can't hang waiting on a handshake that was silently dropped.
        future.orTimeout(HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS).whenComplete((u, e) -> {
            if (e != null) {
                disconnect();
            }
        });
        try {
            // The handshake is the ONE write done synchronously — on a brand-new pipe Discord drains at once,
            // and it must go out BEFORE the read thread starts: a blocking read concurrent with the very
            // first write on the same pipe can delay the handshake past Discord's timeout (RandomAccessFile
            // shares one file pointer across read and write), which Discord answers with a "Handshake
            // timeout" close. Every later write goes through the connection's writer thread instead.
            conn.writeFrame(OP_HANDSHAKE, mapper.writeValueAsBytes(mapper.createObjectNode().put("v", 1).put("client_id", clientId)));
        } catch (IOException e) {
            channel = null;
            closeDetached(ch);
            future.completeExceptionally(e);
            return future;
        }
        var thread = new Thread(() -> readLoop(ch), "discord-ipc-read");
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    public synchronized void disconnect() {
        var ch = channel;
        channel = null;
        authenticated = false;
        voiceUsers.clear();
        currentChannelId = null;
        if (ch != null) {
            closeDetached(ch); // never block the caller: on Windows close() stalls until a pending read returns
        }
    }

    /**
     * Closes a transport and stops its writer without blocking the caller. On Windows
     * {@code RandomAccessFile.close()} blocks until a pending blocking {@code readFully} returns (CloseHandle
     * waits on the in-flight ReadFile), so closing on the calling thread — an HTTP request, the scheduler, or
     * the read thread — would stall it and everything waiting on this client's monitor with it. The close
     * runs on a throwaway daemon thread; if the OS never unblocks the read, only that thread (and the idle
     * writer) linger, bounded to one per disconnect.
     */
    private static void closeDetached(Channel ch) {
        var t = new Thread(() -> {
            ch.writer().shutdownNow();
            ch.conn().close();
        }, "discord-ipc-close");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean isConnected() {
        var ch = channel;
        return ch != null && ch.conn().isOpen();
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated && isConnected();
    }

    /** Best-effort keep-alive; a failed write drops the connection (via {@link #writeFrame}) so the next health-check reconnects. */
    public void ping() {
        writeFrame(OP_PING, "{}".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Enqueues one framed message on the current connection's writer thread and returns at once — the caller
     * never blocks on the pipe. The returned future completes when the bytes are written, or fails if there
     * is no live connection or the write errors (which also drops the connection so the next check reconnects).
     */
    private CompletableFuture<Void> writeFrame(int opcode, byte[] payload) {
        var ch = channel;
        if (ch == null || !ch.conn().isOpen()) {
            return CompletableFuture.failedFuture(new IOException("Discord IPC not connected"));
        }
        var done = new CompletableFuture<Void>();
        try {
            ch.writer().execute(() -> {
                try {
                    ch.conn().writeFrame(opcode, payload);
                    done.complete(null);
                } catch (IOException e) {
                    done.completeExceptionally(e);
                    disconnect(); // a failed write means the pipe is gone — drop it so the next check reconnects
                }
            });
        } catch (RejectedExecutionException e) {
            done.completeExceptionally(new IOException("Discord IPC writer stopped", e));
        }
        return done;
    }

    private void readLoop(Channel ch) {
        var conn = ch.conn();
        try {
            while (conn.isOpen()) {
                dispatch(conn, conn.readFrame());
            }
        } catch (IOException e) {
            log.debug("Discord IPC read ended: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Discord IPC read loop error", e);
        } finally {
            onConnectionDropped(ch);
        }
    }

    private synchronized void onConnectionDropped(Channel ch) {
        ch.writer().shutdownNow();
        ch.conn().close(); // the read has already ended here, so this close does not block
        if (channel == ch) { // only react if this thread still owns the live connection
            channel = null;
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
        return request(cmd, evt, args, REQUEST_TIMEOUT_MS);
    }

    private CompletableFuture<JsonNode> request(String cmd, @Nullable String evt, @Nullable ObjectNode args, long timeoutMs) {
        var future = new CompletableFuture<JsonNode>();
        if (!isConnected()) {
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
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(node);
        } catch (IOException e) {
            future.completeExceptionally(e);
            return future;
        }
        pending.put(nonce, future);
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).whenComplete((r, e) -> pending.remove(nonce));
        writeFrame(OP_FRAME, bytes).exceptionally(e -> {
            pending.remove(nonce);
            future.completeExceptionally(e);
            return null;
        });
        return future;
    }

    private void dispatch(DiscordIpcConnection conn, DiscordIpcConnection.Frame frame) throws IOException {
        switch (frame.opcode()) {
            case OP_FRAME -> handleFrame(mapper.readTree(frame.body()));
            case OP_PING -> writeFrame(OP_PONG, frame.body()); // answered via the writer thread, never inline on the read thread
            case OP_PONG -> { /* keep-alive ack */ }
            case OP_CLOSE -> {
                var node = mapper.readTree(frame.body());
                log.info("Discord IPC closed by server: {}", node);
                trigger(l -> l.onError(new DiscordRpcException(node)));
                conn.close(); // ends the read loop, which fires connectionClosed; safe here — no read is in flight
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
        return request("AUTHORIZE", null, args, AUTHORIZE_TIMEOUT_MS).thenApply(data -> text(data, "code"));
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
