package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SonarClientReadTest {
    private static final String STREAM_VOLUME_SETTINGS = """
            {"masters":{"stream":{"streaming":{"volume":1.0,"muted":false},
                                  "monitoring":{"volume":1.0,"muted":false}}},
             "devices":{"game":{"stream":{"streaming":{"volume":0.25,"muted":false},
                                          "monitoring":{"volume":0.75,"muted":true}}}}}""";

    // Captured live from GET /volumeSettings/classic. Classic mode still carries an empty "stream"
    // sibling per channel, which the parser must ignore.
    private static final String CLASSIC_VOLUME_SETTINGS = """
            {"masters":{"stream":{},"classic":{"volume":0.0,"muted":false}},
             "devices":{"game":{"stream":{},"classic":{"volume":0.8,"muted":true}},
                        "media":{"stream":{},"classic":{"volume":0.3,"muted":false}}}}""";

    private HttpServer sonar;
    private HttpServer gg;

    @AfterEach
    void stop() {
        if (sonar != null) sonar.stop(0);
        if (gg != null) gg.stop(0);
    }

    private void respond(HttpServer server, String path, String body) {
        server.createContext(path, exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private SonarClient clientFor(Path dir, String modeBody, String volumeSettingsPath, String volumeSettingsBody) throws IOException {
        sonar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond(sonar, "/mode", modeBody);
        respond(sonar, volumeSettingsPath, volumeSettingsBody);
        sonar.start();
        var sonarUrl = "http://127.0.0.1:" + sonar.getAddress().getPort();

        gg = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond(gg, "/subApps",
                "{\"subApps\":{\"sonar\":{\"metadata\":{\"webServerAddress\":\"" + sonarUrl + "\"}}}}");
        gg.start();

        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + gg.getAddress().getPort() + "\"}");
        return new SonarClient(coreProps);
    }

    private SonarClient streamClientFor(Path dir) throws IOException {
        return clientFor(dir, "\"stream\"", "/volumeSettings/streamer", STREAM_VOLUME_SETTINGS);
    }

    private SonarClient classicClientFor(Path dir) throws IOException {
        return clientFor(dir, "\"classic\"", "/volumeSettings/classic", CLASSIC_VOLUME_SETTINGS);
    }

    @Test
    void readsTheCurrentMode(@TempDir Path dir) throws Exception {
        assertEquals(SonarMode.stream, streamClientFor(dir).fetchMode().orElseThrow());
    }

    @Test
    void readsBothMixesOfAChannelIndependently(@TempDir Path dir) throws Exception {
        var state = streamClientFor(dir).fetchState(SonarMode.stream).orElseThrow();

        var monitoring = state.level(SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game)).orElseThrow();
        var streaming = state.level(SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Game)).orElseThrow();

        assertEquals(0.75, monitoring.volume(), 0.0001);
        assertEquals(true, monitoring.muted());
        assertEquals(0.25, streaming.volume(), 0.0001);
        assertFalse(streaming.muted());
    }

    @Test
    void readsTheMasterChannel(@TempDir Path dir) throws Exception {
        var state = streamClientFor(dir).fetchState(SonarMode.stream).orElseThrow();

        var master = state.level(SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Master)).orElseThrow();

        assertEquals(1.0, master.volume(), 0.0001);
    }

    @Test
    void readsClassicModeLevels(@TempDir Path dir) throws Exception {
        var state = classicClientFor(dir).fetchState(SonarMode.classic).orElseThrow();

        assertEquals(SonarMode.classic, state.mode());

        var viaMonitoring = state.level(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game)).orElseThrow();
        var viaStreaming = state.level(SonarRoute.of(SonarMode.classic, SonarMix.streaming, SonarChannel.Game)).orElseThrow();
        assertEquals(0.8, viaMonitoring.volume(), 0.0001);
        assertTrue(viaMonitoring.muted());
        assertEquals(0.8, viaStreaming.volume(), 0.0001);
        assertTrue(viaStreaming.muted());

        var master = state.level(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Master)).orElseThrow();
        assertEquals(0.0, master.volume(), 0.0001);
        assertFalse(master.muted());

        // One route per channel (Master, Game, Media) — the two mixes coalesce onto a single classic route.
        assertEquals(3, state.levels().size());
    }

    @Test
    void aVolumeSettingsBodyThatIsNotAnObjectIsNoState(@TempDir Path dir) throws Exception {
        var client = clientFor(dir, "\"stream\"", "/volumeSettings/streamer", "[]");

        assertTrue(client.fetchState(SonarMode.stream).isEmpty());
    }
}
