package com.getpcpanel.integration.sonar;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Talks to SteelSeries Sonar's local HTTP API. Stateless apart from the cached base URL, and owns the
 * discovery chain: coreProps.json gives GG's address, GG's /subApps gives Sonar's own web server. That
 * port is assigned per GG launch, so a stale value is the expected steady state after a GG restart and
 * {@link #invalidate()} is a normal path, not an error path.
 *
 * <p>GG's /subApps hop is HTTPS with a self-signed certificate, so certificate validation is relaxed
 * for that single request, on a dedicated {@code discoveryHttp} client used nowhere else — every other
 * request, including Sonar's own (plain HTTP) API that later callers reach through {@link #get(String)},
 * goes through the default-TLS {@code http} client. {@link #resolve()} additionally requires both the
 * GG address read from coreProps.json and the Sonar address returned by /subApps to be loopback before
 * using them, so a tampered coreProps.json or a spoofed /subApps response naming a non-loopback host is
 * rejected rather than trusted.
 */
@Log4j2
@ApplicationScoped
public class SonarClient {
    private static final Path DEFAULT_CORE_PROPS = Path.of(
            System.getenv().getOrDefault("PROGRAMDATA", "C:\\ProgramData"),
            "SteelSeries", "SteelSeries Engine 3", "coreProps.json");

    private final Path corePropsPath;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final HttpClient discoveryHttp;
    @Nullable private volatile String baseUrl;

    public SonarClient() {
        this(DEFAULT_CORE_PROPS);
    }

    SonarClient(Path corePropsPath) {
        this.corePropsPath = corePropsPath;
        this.http = HttpClient.newBuilder()
                              .connectTimeout(Duration.ofSeconds(2))
                              .build();
        this.discoveryHttp = HttpClient.newBuilder()
                              .connectTimeout(Duration.ofSeconds(2))
                              .sslContext(trustAll())
                              .build();
    }

    public void invalidate() {
        baseUrl = null;
    }

    public Optional<String> baseUrl() {
        var cached = baseUrl;
        if (cached != null) {
            return Optional.of(cached);
        }
        var resolved = resolve().orElse(null);
        baseUrl = resolved;
        return Optional.ofNullable(resolved);
    }

    public Optional<SonarMode> fetchMode() {
        return baseUrl()
                .flatMap(base -> get(base + "/mode"))
                .flatMap(body -> {
                    try {
                        var raw = mapper.readTree(body).asText("");
                        return "classic".equals(raw) ? Optional.of(SonarMode.classic)
                                : "stream".equals(raw) ? Optional.of(SonarMode.stream)
                                : Optional.<SonarMode>empty();
                    } catch (Exception e) {
                        log.debug("Sonar: cannot parse /mode body {}", body, e);
                        return Optional.empty();
                    }
                });
    }

    public Optional<SonarState> fetchState(SonarMode mode) {
        var path = mode == SonarMode.classic ? "/volumeSettings/classic" : "/volumeSettings/streamer";
        return baseUrl().flatMap(base -> get(base + path)).flatMap(body -> parseState(mode, body));
    }

    private Optional<SonarState> parseState(SonarMode mode, String body) {
        try {
            var root = mapper.readTree(body);
            if (root == null || !root.isObject()) {
                // Anything but an object is not a volume-settings answer; reading it would report "no levels".
                return Optional.empty();
            }
            Map<SonarRoute, SonarLevel> levels = new HashMap<>();
            for (var channel : SonarChannel.values()) {
                // The master sits under "masters"; every other channel under "devices".
                var node = channel == SonarChannel.Master
                        ? root.path("masters")
                        : root.path("devices").path(channel.apiId());
                if (node.isMissingNode()) {
                    continue;
                }
                if (mode == SonarMode.classic) {
                    read(node.path("classic")).ifPresent(level ->
                            levels.put(SonarRoute.of(mode, SonarMix.monitoring, channel), level));
                } else {
                    for (var mix : SonarMix.values()) {
                        read(node.path("stream").path(mix.apiId())).ifPresent(level ->
                                levels.put(SonarRoute.of(mode, mix, channel), level));
                    }
                }
            }
            return Optional.of(new SonarState(mode, Map.copyOf(levels)));
        } catch (Exception e) {
            log.debug("Sonar: cannot parse volume settings", e);
            return Optional.empty();
        }
    }

    private static Optional<SonarLevel> read(JsonNode node) {
        if (node.isMissingNode() || !node.hasNonNull("volume")) {
            return Optional.empty();
        }
        return Optional.of(new SonarLevel(node.path("volume").asDouble(), node.path("muted").asBoolean(false)));
    }

    private Optional<String> resolve() {
        try {
            if (!Files.isReadable(corePropsPath)) {
                log.debug("Sonar: no coreProps.json at {}", corePropsPath);
                return Optional.empty();
            }
            var gg = mapper.readTree(Files.readString(corePropsPath)).path("ggEncryptedAddress").asText("");
            if (gg.isBlank()) {
                return Optional.empty();
            }
            var scheme = gg.startsWith("http") ? "" : "https://";
            var ggUrl = scheme + gg + "/subApps";
            if (!isLoopback(ggUrl)) {
                log.debug("Sonar: GG address {} is not loopback", ggUrl);
                return Optional.empty();
            }
            var body = fetchSubApps(ggUrl).orElse(null);
            if (body == null) {
                return Optional.empty();
            }
            var address = mapper.readTree(body).path("subApps").path("sonar")
                                .path("metadata").path("webServerAddress").asText("");
            if (address.isBlank()) {
                return Optional.empty();
            }
            if (!isLoopback(address)) {
                log.debug("Sonar: resolved address {} is not loopback", address);
                return Optional.empty();
            }
            return Optional.of(address);
        } catch (Exception e) {
            log.debug("Sonar: discovery failed", e);
            return Optional.empty();
        }
    }

    public boolean setVolume(SonarRoute route, double value) {
        return put(route.volumePath(value));
    }

    public boolean setMute(SonarRoute route, boolean muted) {
        return put(route.mutePath(muted));
    }

    private boolean put(String path) {
        var base = baseUrl().orElse(null);
        if (base == null) {
            return false;
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(base + path))
                                     .timeout(Duration.ofSeconds(2))
                                     .PUT(HttpRequest.BodyPublishers.noBody())
                                     .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // A non-2xx here (Sonar's 500 "Cannot be called in current mode") means the route is
                // valid but the live mode moved under us, not that the discovered address is stale.
                log.debug("Sonar: PUT {} -> {} {}", path, response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // A transport failure means GG likely restarted on a new port, so the cached address is
            // dropped and the next call re-resolves it.
            log.debug("Sonar: PUT {} failed, re-resolving", path, e);
            invalidate();
            return false;
        }
    }

    /**
     * A GET against Sonar's own API. A transport failure drops the cached address, like {@link #put}: GG
     * assigns Sonar's port per launch, so after a GG restart the cached address answers nothing and only
     * re-resolving finds the new one. A non-2xx answer came from the right server and keeps it.
     */
    Optional<String> get(String url) {
        return get(http, url, true);
    }

    /**
     * The only path to {@code discoveryHttp}: fetches GG's /subApps over the trust-all client. A test
     * subclass overrides this to observe or fake the discovery request without touching the network.
     */
    Optional<String> fetchSubApps(String url) {
        return get(discoveryHttp, url, false);
    }

    private Optional<String> get(HttpClient client, String url, boolean invalidateOnFailure) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                                     .timeout(Duration.ofSeconds(2))
                                     .GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2 ? Optional.of(response.body()) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Sonar: GET {} failed", url, e);
            if (invalidateOnFailure) {
                invalidate();
            }
            return Optional.empty();
        }
    }

    private static boolean isLoopback(String url) {
        try {
            var host = URI.create(url).getHost();
            return host != null && InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * GG's /subApps is HTTPS with a self-signed certificate; this is what lets {@code discoveryHttp}
     * speak to it. Nothing else uses this context.
     */
    private static SSLContext trustAll() {
        try {
            var trustAll = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* trust all */ }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { trustAll }, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build Sonar SSL context", e);
        }
    }
}
