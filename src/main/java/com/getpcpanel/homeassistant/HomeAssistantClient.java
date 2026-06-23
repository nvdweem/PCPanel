package com.getpcpanel.homeassistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.util.SharedHttpClient;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Minimal Home Assistant REST client for one configured server. The Home Assistant API is plain
 * authenticated HTTP+JSON, so this is built on the JDK's {@link HttpClient} rather than pulling in
 * a dependency — only the few calls the integration actually needs are implemented (ping, list
 * states, list services, call a service).
 *
 * @see <a href="https://developers.home-assistant.io/docs/api/rest/">Home Assistant REST API</a>
 */
@Log4j2
public class HomeAssistantClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Getter private final HomeAssistantServer server;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public HomeAssistantClient(HomeAssistantServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
        var u = server.url() == null ? "" : server.url().trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        baseUrl = u;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + (server.token() == null ? "" : server.token().trim()))
                .header("Content-Type", "application/json");
    }

    /** {@code GET /api/} — succeeds only with a reachable server and a valid token. */
    public boolean ping() {
        return ping(REQUEST_TIMEOUT);
    }

    /**
     * {@code GET /api/} with an explicit timeout. The UI status poll uses a short one so a dead server
     * can't pin a REST worker thread for the full 10s service-call timeout.
     */
    public boolean ping(Duration timeout) {
        if (baseUrl.isEmpty()) {
            return false;
        }
        try {
            var resp = SharedHttpClient.get().send(request("/api/").timeout(timeout).GET().build(), HttpResponse.BodyHandlers.ofString());
            return isSuccess(resp.statusCode());
        } catch (Exception e) {
            log.debug("Home Assistant ping failed for {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * {@code POST /api/services/{domain}/{service}} with {@code data} as the JSON body. Sent
     * asynchronously (fire-and-forget) so it never blocks the single command-handler thread — a slow or
     * unreachable HA server must not stall volume knobs, keystrokes, etc. for every device. Mirrors
     * {@code CommandHttpRequest}. Failures are logged, not surfaced (callers ignore the result).
     */
    public boolean callService(String domain, String service, Map<String, Object> data) {
        try {
            var body = mapper.writeValueAsString(data == null ? Map.of() : data);
            SharedHttpClient.get().sendAsync(request("/api/services/" + domain + "/" + service)
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((resp, ex) -> {
                        if (ex != null) {
                            log.warn("Home Assistant service call {}.{} failed: {}", domain, service, ex.getMessage());
                        } else if (!isSuccess(resp.statusCode())) {
                            log.warn("Home Assistant service {}.{} returned HTTP {}: {}", domain, service, resp.statusCode(), resp.body());
                        }
                    });
            return true;
        } catch (Exception e) {
            log.warn("Home Assistant service call {}.{} failed: {}", domain, service, e.getMessage());
            return false;
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
