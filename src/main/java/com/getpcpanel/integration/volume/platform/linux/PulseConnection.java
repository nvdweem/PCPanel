package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.platform.LinuxBuild;

import dev.niels.pulse.PulseClient;
import dev.niels.pulse.PulseException;
import dev.niels.pulse.PulseServerLocator;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * The app's one connection to the PulseAudio protocol server (PulseAudio, or PipeWire's pipewire-pulse), shared by
 * every Linux audio reader and writer. {@link #client()} connects on first use and again after the connection is
 * lost, at most once per {@link #RETRY_MS}; while there is no connection it returns {@code null} and callers fall
 * back to {@code pactl}. {@code pcpanel.pulse.native=false} turns it off, leaving everything on {@code pactl}.
 */
@Log4j2
@ApplicationScoped
@LinuxBuild
class PulseConnection {
    static final String CLIENT_NAME = "PCPanel";
    private static final long RETRY_MS = 5_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    @ConfigProperty(name = "pcpanel.pulse.native", defaultValue = "true")
    boolean enabled;

    @Nullable private volatile PulseClient client;
    private long nextAttempt;
    private volatile String state = "not connected yet";
    private boolean failureLogged;
    private volatile boolean closed;

    /** The open connection, connecting first when there is none; {@code null} when the server can't be reached. */
    @Nullable
    PulseClient client() {
        var current = client;
        if (current != null && current.isOpen()) {
            return current;
        }
        if (!enabled) {
            state = "off (pcpanel.pulse.native=false)";
            return null;
        }
        if (closed) {
            return null;
        }
        return connect();
    }

    private synchronized @Nullable PulseClient connect() {
        var current = client;
        if (current != null && current.isOpen()) {
            return current;
        }
        var now = System.currentTimeMillis();
        if (now < nextAttempt) {
            return null;
        }
        nextAttempt = now + RETRY_MS;
        var env = System.getenv();
        var socket = PulseServerLocator.socket(env).orElse(null);
        if (socket == null) {
            onFailure("no PulseAudio socket found ($PULSE_SERVER, $XDG_RUNTIME_DIR/pulse/native)");
            return null;
        }
        PulseClient connected = null;
        try {
            // media.category=Manager: WirePlumber lets a sandboxed (Flatpak) client only read unless it says it is a
            // mixer; without it every volume, mute and default-device change is refused with "access denied".
            var properties = Map.of(
                    "application.name", CLIENT_NAME,
                    "application.id", "com.getpcpanel.PCPanel",
                    "application.process.id", String.valueOf(ProcessHandle.current().pid()),
                    "media.category", "Manager");
            connected = PulseClient.connect(socket, PulseServerLocator.cookie(env, Path.of(System.getProperty("user.home"))), properties, REQUEST_TIMEOUT);
            var server = connected.serverInfo();
            state = "connected to " + server.serverName() + " " + server.serverVersion() + " at " + socket + ", protocol " + connected.version();
            log.info("PulseAudio protocol: {}", state);
            failureLogged = false;
            client = connected;
            return connected;
        } catch (IOException | RuntimeException e) {
            if (connected != null) {
                connected.close();
            }
            onFailure("unable to connect to " + socket + ": " + e.getMessage());
            return null;
        }
    }

    private void onFailure(String reason) {
        state = reason + "; using pactl";
        if (!failureLogged) {
            failureLogged = true;
            log.warn("PulseAudio protocol: {}", state);
        } else {
            log.debug("PulseAudio protocol: {}", state);
        }
    }

    /** Drops {@code broken} after a failed request, so the next {@link #client()} connects anew right away. */
    synchronized void lost(PulseClient broken, Exception reason) {
        if (client == broken) {
            log.info("PulseAudio protocol connection lost: {}", reason.getMessage());
            state = "connection lost: " + reason.getMessage();
            client = null;
            nextAttempt = 0;
        }
        broken.close();
    }

    String state() {
        return state;
    }

    /** Whether the app is shutting down, so a connection closing is expected. */
    boolean isClosed() {
        return closed;
    }

    @PreDestroy
    void close() {
        closed = true;
        var current = client;
        if (current != null) {
            current.close();
        }
    }
}
