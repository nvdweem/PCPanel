package com.getpcpanel.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandMedia.VolumeButton;
import com.getpcpanel.spring.ConditionalOnMac;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Controls Music.app or Spotify through AppleScript, macOS has no media-key API that is safely reachable from Java.
 */
@Log4j2
@Service
@ConditionalOnMac
@RequiredArgsConstructor
public class OsxMediaControl {
    private static final AtomicBoolean warnedFailure = new AtomicBoolean();
    private final ProcessHelper processHelper;
    private final Set<VolumeButton> warnedUnsupported = ConcurrentHashMap.newKeySet();

    public void execute(VolumeButton button, boolean spotify) {
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
        try {
            var process = processHelper.builder("osascript", "-e", script).redirectErrorStream(true).start();
            var watcher = new Thread(() -> warnOnFailure(process, verb, app), "osascript result watcher");
            watcher.setDaemon(true);
            watcher.start();
        } catch (IOException e) {
            log.error("Unable to send '{}' to {}", verb, app, e);
        }
    }

    private static void warnOnFailure(Process process, String verb, String app) {
        try {
            var output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0 && !warnedFailure.getAndSet(true)) {
                log.warn("Sending '{}' to {} failed: {}. Allow PCPanel to control Music/Spotify in System Settings > Privacy & Security > Automation",
                        verb, app, StringUtils.trimToEmpty(output));
            }
        } catch (Exception e) {
            log.debug("Unable to determine osascript result for '{}' to {}", verb, app, e);
        }
    }
}
