package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SonarClientDiscoveryTest {
    private HttpServer gg;

    @AfterEach
    void stop() {
        if (gg != null) {
            gg.stop(0);
        }
    }

    private int startGg(String sonarAddress) throws IOException {
        gg = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gg.createContext("/subApps", exchange -> {
            var body = """
                    {"subApps":{"sonar":{"isEnabled":true,"isReady":true,"isRunning":true,
                    "metadata":{"webServerAddress":"%s"}}}}""".formatted(sonarAddress);
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        gg.start();
        return gg.getAddress().getPort();
    }

    @Test
    void resolvesSonarAddressThroughCorePropsAndSubApps(@TempDir Path dir) throws Exception {
        var port = startGg("http://127.0.0.1:53972");
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + port + "\"}");

        var client = new SonarClient(coreProps);

        assertEquals("http://127.0.0.1:53972", client.baseUrl().orElseThrow());
    }

    @Test
    void returnsEmptyWhenCorePropsIsMissing(@TempDir Path dir) {
        var client = new SonarClient(dir.resolve("absent.json"));

        assertTrue(client.baseUrl().isEmpty());
    }

    @Test
    void invalidateForcesReResolution(@TempDir Path dir) throws Exception {
        var port = startGg("http://127.0.0.1:53972");
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + port + "\"}");
        var client = new SonarClient(coreProps);
        assertEquals("http://127.0.0.1:53972", client.baseUrl().orElseThrow());

        // GG restarts on a new port; the old cached value must not survive invalidate().
        gg.stop(0);
        var newPort = startGg("http://127.0.0.1:60001");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + newPort + "\"}");
        client.invalidate();

        assertEquals("http://127.0.0.1:60001", client.baseUrl().orElseThrow());
    }

    @Test
    void bareAddressWithNoSchemeIsNotReachedOverPlainHttp(@TempDir Path dir) throws Exception {
        // ggEncryptedAddress with no scheme gets "https://" prepended; this stub only speaks plain HTTP,
        // so the request must fail (proving https:// really was attempted) and resolution comes back empty.
        var port = startGg("http://127.0.0.1:53972");
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"127.0.0.1:" + port + "\"}");

        var client = new SonarClient(coreProps);

        assertTrue(client.baseUrl().isEmpty());
    }

    @Test
    void nonLoopbackGgAddressIsRejectedWithoutSendingARequest(@TempDir Path dir) throws Exception {
        // A literal IP so isLoopback() needs no DNS lookup. fetchSubApps is stubbed to succeed with a
        // valid, loopback Sonar address, so the only thing that can be keeping baseUrl() empty is the
        // pre-request guard never calling it — the invocation count proves that directly.
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://192.0.2.1:1234\"}");
        var client = new CountingSonarClient(coreProps);

        var result = client.baseUrl();

        assertTrue(result.isEmpty());
        assertEquals(0, client.fetchSubAppsCalls);
    }

    private static class CountingSonarClient extends SonarClient {
        private int fetchSubAppsCalls;

        CountingSonarClient(Path corePropsPath) {
            super(corePropsPath);
        }

        @Override
        Optional<String> fetchSubApps(String url) {
            fetchSubAppsCalls++;
            return Optional.of("""
                    {"subApps":{"sonar":{"metadata":{"webServerAddress":"http://127.0.0.1:53972"}}}}""");
        }
    }

    @Test
    void nonLoopbackResolvedSonarAddressIsRejected(@TempDir Path dir) throws Exception {
        var port = startGg("http://192.0.2.1:53972");
        var coreProps = dir.resolve("coreProps.json");
        Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + port + "\"}");

        var client = new SonarClient(coreProps);

        assertTrue(client.baseUrl().isEmpty());
    }
}
