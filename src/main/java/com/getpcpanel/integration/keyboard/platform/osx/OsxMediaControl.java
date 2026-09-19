package com.getpcpanel.integration.keyboard.platform.osx;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Controls Music.app or Spotify through AppleScript, macOS has no media-key API that is safely reachable from Java.
 */
@Log4j2
@ApplicationScoped
@MacBuild
@RequiredArgsConstructor
class OsxMediaControl {
    private static final Duration OSASCRIPT_TIMEOUT = Duration.ofSeconds(10);
    private static final AtomicBoolean warnedFailure = new AtomicBoolean();
    private final ProcessHelper processHelper;
    private final Set<VolumeButton> warnedUnsupported = ConcurrentHashMap.newKeySet();

    void execute(VolumeButton button, boolean spotify) {
        var verb = switch (button) {
            case playPause -> "playpause";
            case next -> "next track";
            case prev -> "previous track";
            case stop -> "pause"; // No stop verb, pause is the closest equivalent
            case mute -> null;
        };
        if (verb == null) {
            if (warnedUnsupported.add(button)) {
                log.warn("Media action '{}' has no AppleScript equivalent on macOS, ignoring", button);
            }
            return;
        }

        var app = spotify ? "Spotify" : "Music";
        // The 'is running' guard prevents AppleScript from launching the player as a side effect
        var script = "if application \"%s\" is running then tell application \"%s\" to %s".formatted(app, app, verb);
        // Off the command thread: AppleScript waits on the player, which can take a moment to answer.
        var sender = new Thread(() -> send(script, verb, app), "osascript sender");
        sender.setDaemon(true);
        sender.start();
    }

    private void send(String script, String verb, String app) {
        try {
            var result = processHelper.run(OSASCRIPT_TIMEOUT, "osascript", "-e", script);
            if (!result.timedOut() && result.exitCode() != 0 && !warnedFailure.getAndSet(true)) {
                log.warn("Sending '{}' to {} failed: {}. Allow PCPanel to control Music/Spotify in System Settings > Privacy & Security > Automation",
                        verb, app, StringUtils.trimToEmpty(String.join("\n", result.stderr())));
            }
        } catch (IOException e) {
            log.error("Unable to send '{}' to {}", verb, app, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
