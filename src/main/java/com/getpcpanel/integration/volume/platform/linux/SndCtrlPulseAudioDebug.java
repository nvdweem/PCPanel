package com.getpcpanel.integration.volume.platform.linux;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.getpcpanel.platform.LinuxBuild;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@LinuxBuild
public class SndCtrlPulseAudioDebug {
    /** Clipboard helpers tried in order: Wayland first, then the two common X11 tools. */
    private static final String[][] CLIPBOARD_COMMANDS = {
            { "wl-copy" },
            { "xclip", "-selection", "clipboard" },
            { "xsel", "--clipboard", "--input" },
    };

    @Inject
    PulseAudioWrapper paWrapper;
    @Inject
    PulseAudioEventListener paEventListener;

    public void copyDebugOutput() {
        var output = StreamEx.of(paWrapper.getDebugOutput())
                             .append(paEventListener.getDebugOutput())
                             .joining("\n".repeat(5));
        // Use the desktop's native clipboard tool instead of java.awt.Toolkit (avoids loading AWT).
        var bytes = output.getBytes(StandardCharsets.UTF_8);
        for (var command : CLIPBOARD_COMMANDS) {
            try {
                var process = new ProcessBuilder(command).redirectErrorStream(true).start();
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(bytes);
                }
                return;
            } catch (Exception e) {
                log.debug("Clipboard tool {} unavailable, trying next", command[0], e);
            }
        }
        log.warn("Unable to copy debug output to clipboard; install wl-clipboard, xclip or xsel");
    }
}
