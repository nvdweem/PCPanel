package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SonarClientWriteTest {
    private final List<String> seen = new ArrayList<>();
    private HttpServer sonar;
    private HttpServer gg;
    private int status = 200;

    // The repo's default test-instance lifecycle is per_class (junit-platform.properties), so this
    // instance's fields outlive a single test method; reset them before each one runs.
    @BeforeEach
    void reset() {
        seen.clear();
        status = 200;
    }

    @AfterEach
    void stop() {
        if (sonar != null) sonar.stop(0);
        if (gg != null) gg.stop(0);
    }

    private SonarClient clientFor(Path dir) throws Exception {
        sonar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sonar.createContext("/", exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        sonar.start();
        var sonarUrl = "http://127.0.0.1:" + sonar.getAddress().getPort();

        gg = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gg.createContext("/subApps", exchange -> {
            var body = "{\"subApps\":{\"sonar\":{\"metadata\":{\"webServerAddress\":\"" + sonarUrl + "\"}}}}";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        gg.start();

        var coreProps = dir.resolve("coreProps.json");
        // Scheme-less per the brief's fixture; SonarClient prepends "https://" to a scheme-less
        // address, so the stub GG server (plain HTTP) is given an explicit "http://" scheme here.
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + gg.getAddress().getPort() + "\"}");
        return new SonarClient(coreProps);
    }

    @Test
    void putsTheStreamerVolumeRoute(@TempDir Path dir) throws Exception {
        var client = clientFor(dir);

        assertTrue(client.setVolume(SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Aux), 0.37));

        assertEquals("PUT /volumeSettings/streamer/streaming/aux/Volume/0.3700", seen.get(0));
    }

    @Test
    void putsTheClassicMuteRoute(@TempDir Path dir) throws Exception {
        var client = clientFor(dir);

        assertTrue(client.setMute(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Media), true));

        assertEquals("PUT /volumeSettings/classic/media/Mute/true", seen.get(0));
    }

    @Test
    void reportsFailureWhenSonarRejectsTheMode(@TempDir Path dir) throws Exception {
        status = 500; // Sonar's "Cannot be called in current mode"
        var client = clientFor(dir);

        assertFalse(client.setVolume(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game), 0.5));
    }

    /**
     * A 500 means the route is valid but the mode moved under us, not that discovery is stale — so the
     * cached address must survive it. A transport failure (here: no Sonar/GG listening at all) means the
     * opposite, so it must invalidate and force the next {@code baseUrl()} call to re-resolve. Stubbing
     * {@link SonarClient#fetchSubApps} and counting its calls observes re-discovery directly.
     */
    @Test
    void onlyATransportFailureInvalidatesTheCachedAddress(@TempDir Path dir) throws Exception {
        sonar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sonar.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        sonar.start();
        var sonarPort = sonar.getAddress().getPort();
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:1\"}");
        var client = new CountingSonarClient(coreProps, "http://127.0.0.1:" + sonarPort);
        var route = SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game);

        status = 500;
        assertFalse(client.setVolume(route, 0.5));
        assertEquals(1, client.fetchSubAppsCalls, "a mode-rejection PUT must not re-trigger discovery");

        sonar.stop(0);
        assertFalse(client.setVolume(route, 0.5), "no listener at the cached address is a transport failure");
        // The discriminating assertion: invalidate() itself never calls fetchSubApps (that happens lazily,
        // only on the next baseUrl()), so the call count right after THIS call is what proves this call's
        // transport failure invalidated the cache, rather than the earlier 500 having wrongly done so. If
        // the two branches were swapped (invalidate on non-2xx, not on transport failure), the earlier 500
        // would have invalidated the cache, forcing this call to re-resolve *before* even sending its PUT
        // — pushing the count to 2 here already, one call too early.
        assertEquals(1, client.fetchSubAppsCalls, "this call's own transport failure - not the earlier 500 - must be what invalidates");
        assertFalse(client.setVolume(route, 0.5));
        assertEquals(2, client.fetchSubAppsCalls, "a transport failure must invalidate and re-resolve on the next call");
    }

    private static class CountingSonarClient extends SonarClient {
        private final String sonarAddress;
        private int fetchSubAppsCalls;

        CountingSonarClient(Path corePropsPath, String sonarAddress) {
            super(corePropsPath);
            this.sonarAddress = sonarAddress;
        }

        @Override
        Optional<String> fetchSubApps(String url) {
            fetchSubAppsCalls++;
            return Optional.of("{\"subApps\":{\"sonar\":{\"metadata\":{\"webServerAddress\":\"" + sonarAddress + "\"}}}}");
        }
    }
}
