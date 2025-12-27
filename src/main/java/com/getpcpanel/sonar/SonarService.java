package com.getpcpanel.sonar;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class SonarService {
    private static final Duration BASE_URL_CACHE_TTL = Duration.ofSeconds(5);
    private static final String DEFAULT_GG_ADDRESS = "127.0.0.1:6327";
    private static final String GG_SUB_APPS_PATH = "/subApps";
    private static final String SONAR_MODE_PATH = "/mode/";
    private static final String SONAR_VOLUME_PATH_FMT_STREAMER = "/volumeSettings/streamer/%s/%s/Volume/%s";
    private static final String SONAR_VOLUME_PATH_FMT_CLASSIC = "/volumeSettings/classic/%s/Volume/%s";

    private final SaveService saveService;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedBaseUrl> cachedBaseUrl = new AtomicReference<>();

    private final HttpClient httpsClient = buildInsecureClient();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public void setVolume(SonarMode mode, SonarTarget target, SonarChannel channel, float volume) {
        var baseUrl = resolveSonarBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            log.warn("SteelSeries Sonar address not found. Check GG is running and coreProps path.");
            return;
        }

        var resolvedMode = resolveMode(mode, baseUrl).orElse(SonarMode.STREAMER);
        var clamped = Math.min(1.0f, Math.max(0.0f, volume));
        var volumeText = String.format(Locale.US, "%.4f", clamped);
        var path = buildVolumePath(resolvedMode, target, channel, volumeText);
        if (path == null) {
            log.warn("Unsupported Sonar mode: {}", resolvedMode);
            return;
        }

        var url = joinUrl(baseUrl, path);
        sendPut(url);
    }

    private Optional<SonarMode> resolveMode(SonarMode mode, String baseUrl) {
        if (mode != SonarMode.AUTO) {
            return Optional.of(mode);
        }
        var url = joinUrl(baseUrl, SONAR_MODE_PATH);
        try {
            var request = HttpRequest.newBuilder(new URI(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            var body = response.body() == null ? "" : response.body().trim().toLowerCase(Locale.US);
            if (body.contains("streamer")) {
                return Optional.of(SonarMode.STREAMER);
            }
            if (body.contains("classic")) {
                return Optional.of(SonarMode.CLASSIC);
            }
            try {
                var node = objectMapper.readTree(body);
                var modeNode = node.path("mode").asText("");
                if ("streamer".equalsIgnoreCase(modeNode)) {
                    return Optional.of(SonarMode.STREAMER);
                }
                if ("classic".equalsIgnoreCase(modeNode)) {
                    return Optional.of(SonarMode.CLASSIC);
                }
            } catch (IOException ignored) {
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            log.debug("Failed to read Sonar mode: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private @Nullable String buildVolumePath(SonarMode mode, SonarTarget target, SonarChannel channel, String volume) {
        if (mode == SonarMode.STREAMER) {
            return SONAR_VOLUME_PATH_FMT_STREAMER.formatted(target.path, channel.path, volume);
        }
        if (mode == SonarMode.CLASSIC) {
            return SONAR_VOLUME_PATH_FMT_CLASSIC.formatted(channel.path, volume);
        }
        return null;
    }

    private void sendPut(String url) {
        try {
            var request = HttpRequest.newBuilder(new URI(url))
                    .timeout(Duration.ofSeconds(2))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.debug("Sonar request failed: {} -> {}", url, response.statusCode());
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            log.debug("Sonar request failed: {} ({})", url, e.getMessage());
        }
    }

    private String resolveSonarBaseUrl() {
        var cached = cachedBaseUrl.get();
        if (cached != null && !cached.isExpired()) {
            return cached.baseUrl;
        }

        var found = fetchSonarBaseUrl();
        if (StringUtils.isNotBlank(found)) {
            cachedBaseUrl.set(new CachedBaseUrl(found));
            return found;
        }
        return cached != null ? cached.baseUrl : "";
    }

    private String fetchSonarBaseUrl() {
        var ggAddress = readGgAddress();
        var subAppsUrl = buildGgUrl(ggAddress, GG_SUB_APPS_PATH);
        try {
            var request = HttpRequest.newBuilder(new URI(subAppsUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = httpsClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return "";
            }
            var node = objectMapper.readTree(response.body());
            return findSonarWebServerAddress(node);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            log.debug("Failed to read GG subApps: {}", e.getMessage());
            return "";
        }
    }

    private String findSonarWebServerAddress(JsonNode root) {
        var subApps = root.path("subApps");
        var address = findWebServerAddress(subApps);
        if (StringUtils.isNotBlank(address)) {
            return address;
        }
        return findWebServerAddress(root);
    }

    private String findWebServerAddress(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            for (var item : node) {
                if (isSonarNode(item)) {
                    var address = extractWebServerAddress(item);
                    if (StringUtils.isNotBlank(address)) {
                        return address;
                    }
                }
            }
            return "";
        }
        if (node.isObject()) {
            if (node.has("sonar")) {
                var address = extractWebServerAddress(node.path("sonar"));
                if (StringUtils.isNotBlank(address)) {
                    return address;
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey().toLowerCase(Locale.US).contains("sonar") || isSonarNode(entry.getValue())) {
                    var address = extractWebServerAddress(entry.getValue());
                    if (StringUtils.isNotBlank(address)) {
                        return address;
                    }
                }
            }
        }
        return "";
    }

    private boolean isSonarNode(JsonNode node) {
        var name = node.path("name").asText("");
        if ("sonar".equalsIgnoreCase(name)) {
            return true;
        }
        var id = node.path("id").asText("");
        return "sonar".equalsIgnoreCase(id);
    }

    private String extractWebServerAddress(JsonNode node) {
        var metadata = node.path("metadata");
        var webServerAddress = metadata.path("webServerAddress").asText("");
        if (StringUtils.isNotBlank(webServerAddress)) {
            return webServerAddress;
        }
        var address = node.path("address").asText("");
        var encrypted = node.path("encryptedAddress").asText("");
        var endpoint = node.path("endpoint").asText("");
        return firstNonBlank(address, encrypted, endpoint);
    }

    private String readGgAddress() {
        var save = saveService.get();
        var path = StringUtils.defaultIfBlank(save.getSonarCorePropsPath(), Save.DEFAULT_SONAR_CORE_PROPS_PATH);
        var ggAddress = DEFAULT_GG_ADDRESS;
        try {
            var content = Files.readString(Path.of(path));
            var node = objectMapper.readTree(content);
            var ggEncrypted = node.path("ggEncryptedAddress").asText("");
            if (StringUtils.isNotBlank(ggEncrypted)) {
                ggAddress = ggEncrypted;
            }
        } catch (IOException e) {
            log.debug("Failed to read coreProps.json: {}", e.getMessage());
        }
        return ggAddress;
    }

    private String buildGgUrl(String ggAddress, String path) {
        var base = ggAddress.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private String joinUrl(String baseUrl, String path) {
        var base = baseUrl.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static HttpClient buildInsecureClient() {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new InsecureTrustManager() }, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to init SSL context", e);
        }
    }

    private static class InsecureTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }

    private static class CachedBaseUrl {
        private final String baseUrl;
        private final long expiresAt;

        CachedBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            this.expiresAt = System.currentTimeMillis() + BASE_URL_CACHE_TTL.toMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public enum SonarMode {
        AUTO,
        STREAMER,
        CLASSIC
    }

    public enum SonarTarget {
        MONITORING("monitoring"),
        STREAMING("streaming");

        private final String path;

        SonarTarget(String path) {
            this.path = path;
        }
    }

    public enum SonarChannel {
        MASTER("master"),
        GAME("game"),
        CHAT_RENDER("chatRender"),
        MEDIA("media"),
        AUX("aux"),
        CHAT_CAPTURE("chatCapture");

        private final String path;

        SonarChannel(String path) {
            this.path = path;
        }
    }
}
