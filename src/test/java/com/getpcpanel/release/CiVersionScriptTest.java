package com.getpcpanel.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code packaging/ci-version.sh}, the single place CI decides what version a build produces.
 *
 * <p>The rule it encodes is load-bearing for the branching model: a {@code vX.Y.Z} tag builds exactly
 * that version, and <em>nothing else</em> ever puts a release version in the working tree. That is what
 * lets a maintenance branch (releases/2.0) merge forward into main without conflicting on pom.xml or
 * the AppStream metainfo — the failure this replaced, where every forward merge collided on the
 * version line because both branches edited it from a common ancestor.
 *
 * <p>The logic used to exist as four slightly different copies inline in the workflow (three different
 * ways of grepping the pom), which is exactly how such things drift. Skipped when bash is unavailable;
 * every CI runner (Windows included, via Git Bash) has it.
 */
class CiVersionScriptTest {
    /**
     * Deliberately RELATIVE. On Windows the first {@code bash} on PATH is usually WSL's
     * ({@code C:\Windows\System32\bash.exe}), which cannot open a {@code D:\...} argument; given a
     * relative path with the working directory at the project root it resolves fine, as does Git Bash.
     */
    private static final String SCRIPT = "packaging/ci-version.sh";

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

    private static String bash;

    @BeforeAll
    static void locateScript() {
        assumeTrue(Files.isRegularFile(Path.of(SCRIPT)), "packaging/ci-version.sh not found");
        bash = findWorkingBash();
        assumeTrue(bash != null, "no working bash on this machine");
    }

    /** A candidate counts only if it actually runs something — merely existing is not enough (see WSL). */
    private static String findWorkingBash() {
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

    /** Runs the script for a ref and returns its KEY=VALUE output as a map. */
    private static Map<String, String> run(String ref, String runNumber) throws Exception {
        var pb = new ProcessBuilder(bash, SCRIPT, ref, runNumber);
        pb.redirectErrorStream(true);
        var process = pb.start();
        String out;
        try (var in = process.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "ci-version.sh timed out");
        assertEquals(0, process.exitValue(), "ci-version.sh failed:\n" + out);

        var result = new HashMap<String, String>();
        for (var line : out.split("\\R")) {
            var eq = line.indexOf('=');
            if (eq > 0) {
                result.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return result;
    }

    @Test
    void versionTagBuildsExactlyThatVersion() throws Exception {
        var out = run("refs/tags/v2.0.85", "77");
        assertEquals("2.0.85", out.get("version"));
        assertEquals("true", out.get("isRelease"));
        // Both overrides are required: baseversion supplies the number, snapshot empties the -SNAPSHOT
        // suffix so the app self-reports a final version to the update check.
        assertEquals("-Dproject.baseversion=2.0.85 -Dproject.snapshot=", out.get("mvnVersionArgs"));
    }

    @Test
    void aPatchTagOnAMaintenanceLineNeedsNoFileEdit() throws Exception {
        // The whole point: releasing 2.0.86 off releases/2.0 touches no versioned file.
        var out = run("refs/tags/v2.0.86", "120");
        assertEquals("2.0.86", out.get("version"));
        assertEquals("true", out.get("isRelease"));
    }

    @Test
    void maintenanceBranchPushIsASnapshotNotARelease() throws Exception {
        var out = run("refs/heads/releases/2.0", "77");
        assertEquals("false", out.get("isRelease"));
        assertTrue(out.get("version").endsWith(".77"), "expected a build-numbered snapshot, got " + out.get("version"));
        assertEquals("", out.get("mvnVersionArgs"), "a snapshot must not override the pom version");
    }

    @Test
    void mainIsASnapshot() throws Exception {
        var out = run("refs/heads/main", "77");
        assertEquals("false", out.get("isRelease"));
        assertTrue(out.get("version").endsWith(".77"), "expected a build-numbered snapshot, got " + out.get("version"));
    }

    /**
     * CI publishes its rolling snapshots under {@code latest-<branch>} tags. Those must never be
     * mistaken for a release, or every snapshot publish would re-trigger a stable build of itself.
     */
    @Test
    void rollingSnapshotTagIsNotARelease() throws Exception {
        var out = run("refs/tags/latest-main", "77");
        assertEquals("false", out.get("isRelease"));
    }
}
