package dev.niels.pulse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * Finds the server's socket and the authentication cookie the way {@code libpulse} does for a local server:
 * {@code $PULSE_SERVER} first, then {@code $XDG_RUNTIME_DIR/pulse/native}; the cookie from {@code $PULSE_COOKIE} or the
 * user's config directory. Network servers ({@code tcp:} entries) are not supported.
 */
public final class PulseServerLocator {
    private PulseServerLocator() {
    }

    /** The first candidate socket that exists. */
    public static Optional<Path> socket(Map<String, String> env) {
        return socketCandidates(env).stream().filter(Files::exists).findFirst();
    }

    static List<Path> socketCandidates(Map<String, String> env) {
        var candidates = new ArrayList<Path>();
        for (var entry : StringUtils.split(StringUtils.defaultString(env.get("PULSE_SERVER")))) {
            // An entry may be limited to one machine with a "{machine-id}" prefix.
            var server = entry.startsWith("{") && entry.contains("}") ? entry.substring(entry.indexOf('}') + 1) : entry;
            if (server.startsWith("unix:")) {
                candidates.add(Path.of(server.substring("unix:".length())));
            } else if (server.startsWith("/")) {
                candidates.add(Path.of(server));
            }
        }
        var runtimeDir = env.get("XDG_RUNTIME_DIR");
        if (StringUtils.isNotBlank(runtimeDir)) {
            candidates.add(Path.of(runtimeDir, "pulse", "native"));
        }
        return candidates;
    }

    /**
     * The cookie PulseAudio authenticates a client with, or all zeros when there is none — enough for PipeWire, which
     * does not check it.
     */
    public static byte[] cookie(Map<String, String> env, Path home) {
        for (var candidate : cookieCandidates(env, home)) {
            try {
                if (Files.isRegularFile(candidate)) {
                    var cookie = Files.readAllBytes(candidate);
                    if (cookie.length >= PulseClient.COOKIE_LENGTH) {
                        return cookie;
                    }
                }
            } catch (IOException | SecurityException e) {
                // Unreadable: try the next one.
            }
        }
        return new byte[PulseClient.COOKIE_LENGTH];
    }

    static List<Path> cookieCandidates(Map<String, String> env, Path home) {
        var candidates = new ArrayList<Path>();
        var explicit = env.get("PULSE_COOKIE");
        if (StringUtils.isNotBlank(explicit)) {
            candidates.add(Path.of(explicit));
        }
        var configHome = env.get("XDG_CONFIG_HOME");
        if (StringUtils.isNotBlank(configHome)) {
            candidates.add(Path.of(configHome, "pulse", "cookie"));
        }
        candidates.add(home.resolve(".config/pulse/cookie"));
        candidates.add(home.resolve(".pulse-cookie"));
        return candidates;
    }
}
