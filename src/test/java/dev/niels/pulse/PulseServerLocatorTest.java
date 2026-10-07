package dev.niels.pulse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PulseServerLocatorTest {
    @Test
    void pulseServerComesBeforeTheRuntimeDir() {
        var env = Map.of("PULSE_SERVER", "tcp:remote {abc}unix:/run/flatpak/pulse/native /tmp/p", "XDG_RUNTIME_DIR", "/run/user/1000");

        assertEquals(List.of(Path.of("/run/flatpak/pulse/native"), Path.of("/tmp/p"), Path.of("/run/user/1000", "pulse", "native")),
                PulseServerLocator.socketCandidates(env));
    }

    @Test
    void readsTheFirstUsableCookie(@TempDir Path home) throws IOException {
        var cookie = new byte[256];
        cookie[0] = 42;
        Files.createDirectories(home.resolve(".config/pulse"));
        Files.write(home.resolve(".config/pulse/cookie"), cookie);
        Files.write(home.resolve(".pulse-cookie"), new byte[256]);

        assertArrayEquals(cookie, PulseServerLocator.cookie(Map.of("PULSE_COOKIE", home.resolve("missing").toString()), home));
    }

    @Test
    void noCookieIsAllZeros(@TempDir Path home) {
        assertArrayEquals(new byte[256], PulseServerLocator.cookie(Map.of(), home));
    }
}
