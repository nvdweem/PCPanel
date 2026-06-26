package dev.niels.discord.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;

import javax.annotation.Nullable;

/** Opens the first reachable Discord IPC endpoint for the current OS. */
final class DiscordIpcConnections {
    private DiscordIpcConnections() {
    }

    /** Probes the platform's Discord IPC endpoints (pipe/socket indices 0-9) and returns the first that opens, or null. */
    @Nullable
    static DiscordIpcConnection open() {
        return SystemUtils.IS_OS_WINDOWS
                ? WindowsPipeConnection.openFirstAvailable()
                : UnixSocketConnection.openFirstAvailable();
    }

    /**
     * Candidate Unix-socket paths in probe order: each runtime/temp base, plus the Flatpak and snap app
     * sub-directories where a sandboxed Discord places its socket, crossed with indices 0-9.
     */
    static List<Path> unixSocketCandidates() {
        var bases = new ArrayList<String>();
        for (var env : List.of("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")) {
            var v = System.getenv(env);
            if (v != null && !v.isBlank()) {
                bases.add(v);
            }
        }
        bases.add("/tmp");
        bases.add("/var/tmp");
        var subdirs = List.of("", "app/com.discordapp.Discord/", "snap.discord/", "snap.discord-canary/");
        var out = new ArrayList<Path>();
        for (var base : bases) {
            for (var sub : subdirs) {
                for (var i = 0; i < 10; i++) {
                    out.add(Path.of(base, sub + "discord-ipc-" + i));
                }
            }
        }
        return out;
    }
}
