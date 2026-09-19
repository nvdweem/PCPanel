package com.getpcpanel.integration.clipboard.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.getpcpanel.util.os.ProcessHelper;

/**
 * Shared helper for the process-based clipboard writers (macOS {@code pbcopy}, Linux
 * {@code wl-copy}/{@code xclip}/{@code xsel}): pipes text to a command's stdin. Returns whether the tool
 * ran and exited cleanly, so a caller can fall back to the next tool; a missing tool is a {@code false}
 * return, never an exception.
 */
public final class ClipboardProcess {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private ClipboardProcess() {
    }

    /** Pipes {@code text} (UTF-8) to {@code command}'s stdin. Returns true only if it exited 0 in time. */
    public static boolean pipe(ProcessHelper processes, String text, String... command) {
        try {
            return processes.runWithInput(TIMEOUT, text.getBytes(StandardCharsets.UTF_8), command).succeeded();
        } catch (IOException e) {
            return false; // tool not installed / not executable
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
