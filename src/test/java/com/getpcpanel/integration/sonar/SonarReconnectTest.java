package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * GG assigns Sonar's port per launch, so a GG restart moves Sonar to a new address that only
 * re-resolving through coreProps.json and /subApps finds. These run a real GG + Sonar pair on loopback
 * and restart it on new ports.
 */
class SonarReconnectTest {
    private static String volumeSettings(double gameVolume) {
        return """
                {"masters":{"stream":{"streaming":{"volume":1.0,"muted":false},
                                      "monitoring":{"volume":1.0,"muted":false}}},
                 "devices":{"game":{"stream":{"streaming":{"volume":0.5,"muted":false},
                                              "monitoring":{"volume":%s,"muted":false}}}}}""".formatted(gameVolume);
    }

    /** One GG launch: a Sonar web server and the GG endpoint that names it, each on a fresh port. */
    private static final class GgLaunch {
        final HttpServer sonar;
        final HttpServer gg;

        GgLaunch(Path coreProps, double gameVolume) throws IOException {
            sonar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            respond(sonar, "/mode", "\"stream\"");
            respond(sonar, "/volumeSettings/streamer", volumeSettings(gameVolume));
            sonar.start();
            gg = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            respond(gg, "/subApps", "{\"subApps\":{\"sonar\":{\"metadata\":{\"webServerAddress\":\"http://127.0.0.1:"
                    + sonar.getAddress().getPort() + "\"}}}}");
            gg.start();
            Files.writeString(coreProps, "{\"ggEncryptedAddress\":\"http://127.0.0.1:" + gg.getAddress().getPort() + "\"}");
        }

        void stop() {
            sonar.stop(0);
            gg.stop(0);
        }

        private static void respond(HttpServer server, String path, String body) {
            server.createContext(path, exchange -> {
                var bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
        }
    }

    private static double gameMonitoring(SonarService service) {
        return service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume();
    }

    @Test
    void theClientReResolvesAfterATransportFailure(@TempDir Path dir) throws Exception {
        var coreProps = dir.resolve("coreProps.json");
        var launches = new ArrayList<GgLaunch>();
        try {
            launches.add(new GgLaunch(coreProps, 0.25));
            var client = new SonarClient(coreProps);
            assertEquals(SonarMode.stream, client.fetchMode().orElseThrow());

            launches.get(0).stop();
            launches.add(new GgLaunch(coreProps, 0.75));

            assertTrue(client.fetchMode().isEmpty(), "the cached address is gone");
            assertEquals(SonarMode.stream, client.fetchMode().orElseThrow(),
                    "the failed request must drop the cached address so the next one finds the new launch");
        } finally {
            launches.forEach(GgLaunch::stop);
        }
    }

    @Test
    void aPollFollowsGgToItsNewPortWithinOneTick(@TempDir Path dir) throws Exception {
        var coreProps = dir.resolve("coreProps.json");
        var launches = new ArrayList<GgLaunch>();
        try {
            launches.add(new GgLaunch(coreProps, 0.25));
            var service = SonarServiceFixtures.service(new SonarClient(coreProps), true);
            service.setInUse(true);
            service.poll();
            assertTrue(service.isReady(), "precondition: the first launch was found");
            assertEquals(0.25, gameMonitoring(service), 0.0001);

            launches.get(0).stop();
            launches.add(new GgLaunch(coreProps, 0.75));
            service.poll();

            assertTrue(service.isReady(), "one failed request must not drop the integration");
            assertEquals(0.75, gameMonitoring(service), 0.0001);
        } finally {
            launches.forEach(GgLaunch::stop);
        }
    }

    @Test
    void aPollRecoversOnceGgComesBackOnANewPort(@TempDir Path dir) throws Exception {
        var coreProps = dir.resolve("coreProps.json");
        List<GgLaunch> launches = new ArrayList<>();
        try {
            launches.add(new GgLaunch(coreProps, 0.25));
            var service = SonarServiceFixtures.service(new SonarClient(coreProps), true);
            service.setInUse(true);
            service.poll();
            assertTrue(service.isReady(), "precondition: the first launch was found");

            launches.get(0).stop();
            service.poll();
            assertFalse(service.isReady(), "precondition: GG is gone");

            launches.add(new GgLaunch(coreProps, 0.75));
            service.poll();

            assertTrue(service.isReady());
            assertEquals(0.75, gameMonitoring(service), 0.0001);
        } finally {
            launches.forEach(GgLaunch::stop);
        }
    }
}
