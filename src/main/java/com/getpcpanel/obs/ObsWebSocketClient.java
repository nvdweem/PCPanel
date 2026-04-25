package com.getpcpanel.obs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;

/**
 * OBS WebSocket protocol 5 client built on java.net.http.WebSocket.
 *
 * <p>Handles: hello/identify handshake, optional SHA-256 password auth,
 * request/response correlation, InputMuteStateChanged events.
 */
@Log4j2
public class ObsWebSocketClient implements WebSocket.Listener {

    // OBS WebSocket 5 opcodes
    private static final int OP_HELLO = 0;
    private static final int OP_IDENTIFY = 1;
    private static final int OP_IDENTIFIED = 2;
    private static final int OP_EVENT = 5;
    private static final int OP_REQUEST = 6;
    private static final int OP_REQUEST_RESPONSE = 7;

    // EventSubscriptions bit: Inputs (for InputMuteStateChanged)
    private static final int EVENT_SUB_INPUTS = 1 << 3;

    private final ObjectMapper mapper;
    private final String password;
    private final Consumer<Boolean> onConnected;
    private final Consumer<OBSMuteEvent> onMuteChange;

    private WebSocket webSocket;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final StringBuilder textBuffer = new StringBuilder();
    private volatile boolean connected = false;

    public ObsWebSocketClient(ObjectMapper mapper, String password,
                              Consumer<Boolean> onConnected, Consumer<OBSMuteEvent> onMuteChange) {
        this.mapper = mapper;
        this.password = password;
        this.onConnected = onConnected;
        this.onMuteChange = onMuteChange;
    }

    public void connect(String host, int port, long timeoutMs) throws Exception {
        var uri = URI.create("ws://" + host + ":" + port);
        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, this)
                .get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void disconnect() {
        connected = false;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // --- WebSocket.Listener ---

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        textBuffer.append(data);
        ws.request(1);
        if (!last) {
            return null;
        }
        var text = textBuffer.toString();
        textBuffer.setLength(0);
        try {
            handleMessage(mapper.readTree(text));
        } catch (Exception e) {
            log.warn("OBS: failed to handle message", e);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        if (connected) {
            connected = false;
            onConnected.accept(false);
        }
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        log.warn("OBS WebSocket error: {}", error.getMessage());
        if (connected) {
            connected = false;
            onConnected.accept(false);
        }
        pending.values().forEach(f -> f.completeExceptionally(error));
        pending.clear();
    }

    // --- Protocol handling ---

    private void handleMessage(JsonNode msg) throws Exception {
        var op = msg.path("op").asInt(-1);
        var d = msg.path("d");
        switch (op) {
            case OP_HELLO -> identify(d);
            case OP_IDENTIFIED -> {
                connected = true;
                log.info("OBS: connected and authenticated");
                onConnected.accept(true);
            }
            case OP_EVENT -> handleEvent(d);
            case OP_REQUEST_RESPONSE -> {
                var id = d.path("requestId").asText(null);
                var future = id != null ? pending.remove(id) : null;
                if (future != null) {
                    future.complete(d.path("responseData"));
                }
            }
            default -> log.trace("OBS: unhandled opcode {}", op);
        }
    }

    private void identify(JsonNode hello) throws Exception {
        var msg = mapper.createObjectNode();
        msg.put("op", OP_IDENTIFY);
        var data = msg.putObject("d");
        data.put("rpcVersion", 1);
        data.put("eventSubscriptions", EVENT_SUB_INPUTS);
        var authNode = hello.path("authentication");
        if (!authNode.isMissingNode() && !authNode.isNull() && password != null && !password.isBlank()) {
            data.put("authentication", computeAuth(password,
                    authNode.path("salt").asText(),
                    authNode.path("challenge").asText()));
        }
        send(msg);
    }

    private void handleEvent(JsonNode d) {
        var type = d.path("eventType").asText();
        if ("InputMuteStateChanged".equals(type)) {
            var data = d.path("eventData");
            onMuteChange.accept(new OBSMuteEvent(
                    data.path("inputName").asText(),
                    data.path("inputMuted").asBoolean()));
        }
    }

    private static String computeAuth(String password, String salt, String challenge) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        var secret = Base64.getEncoder().encodeToString(
                md.digest((password + salt).getBytes(StandardCharsets.UTF_8)));
        md.reset();
        return Base64.getEncoder().encodeToString(
                md.digest((secret + challenge).getBytes(StandardCharsets.UTF_8)));
    }

    private void send(Object obj) {
        try {
            webSocket.sendText(mapper.writeValueAsString(obj), true);
        } catch (Exception e) {
            log.warn("OBS: failed to send message", e);
        }
    }

    private JsonNode request(String type, ObjectNode fields) throws Exception {
        var id = UUID.randomUUID().toString();
        var msg = mapper.createObjectNode();
        msg.put("op", OP_REQUEST);
        var d = msg.putObject("d");
        d.put("requestType", type);
        d.put("requestId", id);
        if (fields != null) {
            d.set("requestData", fields);
        }
        var future = new CompletableFuture<JsonNode>();
        pending.put(id, future);
        send(msg);
        return future.get(5, TimeUnit.SECONDS);
    }

    // --- High-level OBS operations ---

    public List<String> getSourcesWithAudio() {
        try {
            var resp = request("GetInputList", null);
            var list = new ArrayList<String>();
            resp.path("inputs").forEach(n -> list.add(n.path("inputName").asText()));
            return list;
        } catch (Exception e) {
            log.warn("OBS: GetInputList failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Boolean> getSourcesWithMuteState() {
        var map = new LinkedHashMap<String, Boolean>();
        for (var source : getSourcesWithAudio()) {
            try {
                var fields = mapper.createObjectNode().put("inputName", source);
                var resp = request("GetInputMute", fields);
                map.put(source, resp.path("inputMuted").asBoolean());
            } catch (Exception e) {
                log.trace("OBS: GetInputMute failed for {}: {}", source, e.getMessage());
            }
        }
        return map;
    }

    public List<String> getScenes() {
        try {
            var resp = request("GetSceneList", null);
            var list = new ArrayList<String>();
            resp.path("scenes").forEach(n -> list.add(n.path("sceneName").asText()));
            return list;
        } catch (Exception e) {
            log.warn("OBS: GetSceneList failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** vol is 0–100; converted to OBS volume multiplier 0.0–1.0. */
    public void setSourceVolume(String sourceName, int vol) {
        try {
            var fields = mapper.createObjectNode()
                    .put("inputName", sourceName)
                    .put("inputVolumeMultiplier", vol / 100.0);
            request("SetInputVolume", fields);
        } catch (Exception e) {
            log.warn("OBS: SetInputVolume failed: {}", e.getMessage());
        }
    }

    public void toggleSourceMute(String sourceName) {
        try {
            var fields = mapper.createObjectNode().put("inputName", sourceName);
            request("ToggleInputMute", fields);
        } catch (Exception e) {
            log.warn("OBS: ToggleInputMute failed: {}", e.getMessage());
        }
    }

    public void setSourceMute(String sourceName, boolean mute) {
        try {
            var fields = mapper.createObjectNode()
                    .put("inputName", sourceName)
                    .put("inputMuted", mute);
            request("SetInputMute", fields);
        } catch (Exception e) {
            log.warn("OBS: SetInputMute failed: {}", e.getMessage());
        }
    }

    public void setCurrentScene(String sceneName) {
        try {
            var fields = mapper.createObjectNode().put("sceneName", sceneName);
            request("SetCurrentProgramScene", fields);
        } catch (Exception e) {
            log.warn("OBS: SetCurrentProgramScene failed: {}", e.getMessage());
        }
    }
}
