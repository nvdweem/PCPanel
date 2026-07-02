package com.getpcpanel.integration.homeassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises {@link HomeAssistantClient} against a stub HTTP server (a JDK {@code HttpServer} on a
 * random port): the request shape of {@code ping} and {@code callService} (path, method, bearer
 * header, JSON body), the trailing-slash trimming of the base url, and the failure results for an
 * unauthorized, unreachable, or unconfigured server. {@code callService} is fire-and-forget, so the
 * stub captures each request and the test awaits it with a deadline poll rather than sleeping.
 *
 * <p>The suite uses the {@code per_class} test-instance lifecycle, so all mutable state is reset in
 * {@code @BeforeEach} — it would otherwise leak between test methods.
 */
@DisplayName("HomeAssistantClient HTTP behaviour")
class HomeAssistantClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<CapturedRequest> requests = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private volatile int status;

    private record CapturedRequest(String method, String path, String authorization, String contentType, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        requests.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            exchange.sendResponseHeaders(status, 0);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HomeAssistantClient client(String url, String token) {
        return new HomeAssistantClient(new HomeAssistantServer("id1", "test", url, token), mapper);
    }

    /** Waits for the stub to have captured a request (callService responds before the send completes). */
    private CapturedRequest awaitRequest() throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (requests.isEmpty() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        var req = requests.poll();
        assertNotNull(req, "expected the request to reach the stub server");
        return req;
    }

    @Test
    @DisplayName("ping GETs /api/ with the bearer token and succeeds on 200")
    void pingSuccess() {
        assertTrue(client(baseUrl(), "secret").ping());

        var req = requests.poll();
        assertEquals("GET", req.method());
        assertEquals("/api/", req.path());
        assertEquals("Bearer secret", req.authorization());
    }

    @Test
    @DisplayName("trailing slashes on the base url are trimmed, so the path stays /api/")
    void trailingSlashesTrimmed() {
        assertTrue(client(baseUrl() + "///", "secret").ping());
        assertEquals("/api/", requests.poll().path());
    }

    @Test
    @DisplayName("ping fails on 401, on an unreachable server, and on a blank url")
    void pingFailures() {
        status = 401;
        assertFalse(client(baseUrl(), "wrong").ping());

        var deadUrl = baseUrl();
        server.stop(0);
        assertFalse(client(deadUrl, "secret").ping());

        assertFalse(client("", "secret").ping());
    }

    @Test
    @DisplayName("callService POSTs the data as JSON to /api/services/{domain}/{service}")
    void callServicePostsJson() throws Exception {
        var ok = client(baseUrl(), "secret").callService("light", "turn_on", Map.of("entity_id", "light.living_room"));
        assertTrue(ok);

        var req = awaitRequest();
        assertEquals("POST", req.method());
        assertEquals("/api/services/light/turn_on", req.path());
        assertEquals("Bearer secret", req.authorization());
        assertEquals("application/json", req.contentType());
        assertEquals(Map.of("entity_id", "light.living_room"), mapper.readValue(req.body(), Map.class));
    }

    @Test
    @DisplayName("callService with null data sends an empty JSON object")
    void callServiceNullData() throws Exception {
        assertTrue(client(baseUrl(), "secret").callService("switch", "toggle", null));
        assertEquals("{}", awaitRequest().body());
    }

    @Test
    @DisplayName("a null token sends an empty bearer value instead of throwing")
    void nullTokenPings() {
        assertTrue(client(baseUrl(), null).ping());
        // The JDK client trims the trailing space of the empty "Bearer " value in transit.
        assertEquals("Bearer", requests.poll().authorization());
    }

    @Test
    @DisplayName("getServer returns the configured server")
    void exposesServer() {
        var haServer = new HomeAssistantServer("id1", "test", baseUrl(), "secret");
        assertEquals(haServer, new HomeAssistantClient(haServer, mapper).getServer());
        assertNull(requests.poll(), "construction alone must not touch the server");
    }
}
