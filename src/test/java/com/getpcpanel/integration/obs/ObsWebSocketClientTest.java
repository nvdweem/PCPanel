package com.getpcpanel.integration.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;

/**
 * Drives {@link ObsWebSocketClient} against an in-process fake OBS WebSocket 5 server that validates
 * requests the way obs-websocket does.
 */
class ObsWebSocketClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PASSWORD = "secret";
    private static final String SALT = "c2FsdA==";
    private static final String CHALLENGE = "Y2hhbGxlbmdl";

    private static Vertx vertx;
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    private HttpServer server;
    private ObsWebSocketClient client;

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void setSourceVolumeSendsAVolumeFieldObsAccepts() throws Exception {
        var port = startServer(true);
        client = connect(port, PASSWORD);

        client.setSourceVolume("Desktop Audio", 100);
        client.setSourceVolume("Desktop Audio", 50);
        client.setSourceVolume("Desktop Audio", 0);

        var sets = awaitRequests("SetInputVolume", 3);
        assertEquals(0.0, sets.get(0).path("inputVolumeDb").asDouble(), 1e-6);
        assertEquals(-48.5, sets.get(1).path("inputVolumeDb").asDouble(), 1e-6);
        assertEquals(-97.0, sets.get(2).path("inputVolumeDb").asDouble(), 1e-6);
    }

    @Test
    void aRejectedRequestFailsInsteadOfLookingSuccessful() throws Exception {
        var port = startServer(true);
        client = connect(port, PASSWORD);

        var future = client.requestAsync("SetInputVolume", MAPPER.createObjectNode().put("inputName", "Desktop Audio"));

        var failure = future.handle((r, ex) -> ex).get(5, TimeUnit.SECONDS);
        assertTrue(failure != null && failure.getMessage().contains("You must specify one volume field"), "got " + failure);
    }

    @Test
    void aBurstOfWritesFromManyThreadsAllReachObs() throws Exception {
        var port = startServer(true);
        client = connect(port, PASSWORD);

        var threads = 8;
        var perThread = 50;
        var start = new CountDownLatch(1);
        var pool = new ArrayList<Thread>();
        for (var t = 0; t < threads; t++) {
            var th = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (var i = 0; i < perThread; i++) {
                    client.setSourceVolume("Mic/Aux", i % 101);
                }
            });
            th.start();
            pool.add(th);
        }
        start.countDown();
        for (var th : pool) {
            th.join();
        }

        awaitRequests("SetInputVolume", threads * perThread);
        assertEquals(List.of("Desktop Audio", "Mic/Aux"), client.getSourcesWithAudio());
    }

    @Test
    void connectsWhenObsHasAuthenticationDisabledEvenWithAPasswordSaved() throws Exception {
        var port = startServer(false);
        client = connect(port, PASSWORD);

        assertEquals(List.of("Desktop Audio", "Mic/Aux"), client.getSourcesWithAudio());
    }

    @Test
    void connectsWhenObsHasAuthenticationDisabledAndNoPassword() throws Exception {
        var port = startServer(false);
        client = connect(port, "");

        assertEquals(List.of("Desktop Audio", "Mic/Aux"), client.getSourcesWithAudio());
    }

    @Test
    void doesNotConnectWithAWrongPassword() throws Exception {
        var port = startServer(true);
        client = new ObsWebSocketClient(MAPPER, "wrong", c -> {}, e -> {});
        client.connect("127.0.0.1", port, 5_000);
        Thread.sleep(500);

        assertFalse(client.isConnected());
    }

    private ObsWebSocketClient connect(int port, String password) throws Exception {
        var c = new ObsWebSocketClient(MAPPER, password, connected -> {}, e -> {});
        c.connect("127.0.0.1", port, 5_000);
        for (var i = 0; i < 100 && !c.isConnected(); i++) {
            Thread.sleep(20);
        }
        assertTrue(c.isConnected(), "client did not identify");
        return c;
    }

    private List<JsonNode> awaitRequests(String type, int count) throws InterruptedException {
        List<JsonNode> matching = List.of();
        for (var i = 0; i < 250; i++) {
            synchronized (requests) {
                matching = requests.stream().filter(r -> type.equals(r.path("requestType").asText())).map(r -> r.path("requestData")).toList();
            }
            if (matching.size() >= count) {
                return matching;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("expected " + count + " " + type + " requests, OBS received " + matching.size());
    }

    private int startServer(boolean authRequired) throws Exception {
        server = vertx.createHttpServer()
                                 .webSocketHandler(ws -> handle(ws, authRequired))
                                 .listen(0, "127.0.0.1")
                                 .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        return server.actualPort();
    }

    private void handle(ServerWebSocket ws, boolean authRequired) {
        var hello = MAPPER.createObjectNode().put("op", 0);
        var d = hello.putObject("d").put("obsWebSocketVersion", "5.5.0").put("rpcVersion", 1);
        if (authRequired) {
            d.putObject("authentication").put("salt", SALT).put("challenge", CHALLENGE);
        }
        ws.writeTextMessage(hello.toString());
        ws.textMessageHandler(text -> {
            try {
                var msg = MAPPER.readTree(text);
                switch (msg.path("op").asInt()) {
                    case 1 -> {
                        if (authRequired && !expectedAuth().equals(msg.path("d").path("authentication").asText())) {
                            ws.close((short) 4009, "Authentication failed.");
                            return;
                        }
                        ws.writeTextMessage("{\"op\":2,\"d\":{\"negotiatedRpcVersion\":1}}");
                    }
                    case 6 -> respond(ws, msg.path("d"));
                    default -> {
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void respond(ServerWebSocket ws, JsonNode req) {
        requests.add(req);
        var type = req.path("requestType").asText();
        var data = req.path("requestData");
        var resp = MAPPER.createObjectNode().put("op", 7);
        var d = resp.putObject("d").put("requestType", type).put("requestId", req.path("requestId").asText());
        var status = d.putObject("requestStatus");
        switch (type) {
            case "GetInputList" -> {
                status.put("result", true).put("code", 100);
                var inputs = d.putObject("responseData").putArray("inputs");
                inputs.addObject().put("inputName", "Desktop Audio");
                inputs.addObject().put("inputName", "Mic/Aux");
            }
            case "SetInputVolume" -> {
                if (data.has("inputVolumeMul") == data.has("inputVolumeDb")) {
                    status.put("result", false).put("code", 300).put("comment", "You must specify one volume field.");
                } else {
                    status.put("result", true).put("code", 100);
                }
            }
            default -> status.put("result", true).put("code", 100);
        }
        ws.writeTextMessage(resp.toString());
    }

    private static String expectedAuth() throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        var secret = Base64.getEncoder().encodeToString(md.digest((PASSWORD + SALT).getBytes(StandardCharsets.UTF_8)));
        md.reset();
        return Base64.getEncoder().encodeToString(md.digest((secret + CHALLENGE).getBytes(StandardCharsets.UTF_8)));
    }
}
