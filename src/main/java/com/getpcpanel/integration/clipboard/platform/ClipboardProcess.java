package com.getpcpanel.integration.clipboard.platform;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Shared helper for the process-based clipboard writers (macOS {@code pbcopy}, Linux
 * {@code wl-copy}/{@code xclip}/{@code xsel}): pipes text to a command's stdin. Returns whether the tool
 * ran and exited cleanly, so a caller can fall back to the next tool; a missing tool is a {@code false}
 * return, never an exception.
 */
public final class ClipboardProcess {
    private ClipboardProcess() {
    }

    /** Pipes {@code text} (UTF-8) to {@code command}'s stdin. Returns true only if it exited 0 in time. */
    public static boolean pipe(String text, String... command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(text.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (java.io.IOException e) {
            return false; // tool not installed / not executable
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
