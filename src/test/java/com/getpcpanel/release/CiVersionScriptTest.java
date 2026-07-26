package com.getpcpanel.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    private static final String SCRIPT = "packaging/ci-version.sh";

    private static String bash;

    @BeforeAll
    static void locateScript() {
        assumeTrue(Files.isRegularFile(Path.of(SCRIPT)), "packaging/ci-version.sh not found");
        bash = BashScript.findWorkingBash();
        assumeTrue(bash != null, "no working bash on this machine");
    }

    /** Runs the script for a ref and returns its KEY=VALUE output as a map. */
    private static Map<String, String> run(String ref, String runNumber) throws Exception {
        return BashScript.run(bash, SCRIPT, ref, runNumber);
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
