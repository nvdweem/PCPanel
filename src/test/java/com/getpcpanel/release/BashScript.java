package com.getpcpanel.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs one of the {@code packaging/*.sh} CI scripts and parses its {@code KEY=VALUE} output, so the
 * tests that guard them assert on behaviour rather than on how bash is located.
 *
 * <p>Script paths are deliberately RELATIVE. On Windows the first {@code bash} on PATH is usually WSL's
 * ({@code C:\Windows\System32\bash.exe}), which cannot open a {@code D:\...} argument; given a relative
 * path with the working directory at the project root it resolves fine, as does Git Bash.
 */
final class BashScript {
    /**
     * Candidate bash executables, best first. On a Windows runner the {@code bash} on PATH is the WSL
     * launcher ({@code C:\Windows\System32\bash.exe}), which reports "Windows Subsystem for Linux has
     * no installed distributions" and fails — so Git Bash is tried first, which is also exactly what
     * GitHub Actions' own {@code shell: bash} uses on Windows.
     */
    private static final List<String> BASH_CANDIDATES = List.of(
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            "bash");

    private BashScript() {
    }

    /** A candidate counts only if it actually runs something — merely existing is not enough (see WSL). */
    static String findWorkingBash() {
        for (var candidate : BASH_CANDIDATES) {
            try {
                var pb = new ProcessBuilder(candidate, "-c", "echo pcpanel-bash-ok");
                pb.redirectErrorStream(true);
                var process = pb.start();
                String out;
                try (var in = process.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
                        && out.contains("pcpanel-bash-ok")) {
                    return candidate;
                }
            } catch (IOException e) {
                // candidate not present - try the next
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Runs {@code script} with {@code args} and returns its KEY=VALUE output as a map. */
    static Map<String, String> run(String bash, String script, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(bash);
        command.add(script);
        command.addAll(List.of(args));

        var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        var process = pb.start();
        String out;
        try (var in = process.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), script + " timed out");
        assertEquals(0, process.exitValue(), script + " failed:\n" + out);

        var result = new HashMap<String, String>();
        for (var line : out.split("\\R")) {
            var eq = line.indexOf('=');
            if (eq > 0) {
                result.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return result;
    }
}
