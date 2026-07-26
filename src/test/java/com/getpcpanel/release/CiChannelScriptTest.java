package com.getpcpanel.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code packaging/ci-channel.sh}, the single place CI decides which self-update channels a
 * build publishes to.
 *
 * <p>Linux self-update has two channels ordered by publication rather than by version: {@code latest}
 * (the newest stable release) and {@code latest-snapshot} (the newest build of the development line).
 * A stable release publishes to both, which is what moves a snapshot install onto a final build when
 * one ships while leaving it on the snapshot channel, so the next snapshot reaches it again.
 *
 * <p>The newest-tag lookup is passed in rather than read from git, so the assertions do not depend on
 * which tags happen to exist in the checkout (a shallow CI clone has none).
 */
class CiChannelScriptTest {
    private static final String SCRIPT = "packaging/ci-channel.sh";

    private static String bash;

    @BeforeAll
    static void locateScript() {
        assumeTrue(Files.isRegularFile(Path.of(SCRIPT)), "packaging/ci-channel.sh not found");
        bash = BashScript.findWorkingBash();
        assumeTrue(bash != null, "no working bash on this machine");
    }

    private static Map<String, String> run(String ref, String newestTag) throws Exception {
        return BashScript.run(bash, SCRIPT, ref, newestTag);
    }

    @Test
    void newestReleaseClaimsBothChannels() throws Exception {
        var out = run("refs/tags/v2.0.88", "v2.0.88");
        assertEquals("true", out.get("isRelease"));
        assertEquals("true", out.get("isNewest"));
        assertEquals("v2.0.88", out.get("releaseTag"));
        assertEquals("latest", out.get("zsyncTag"), "the stable AppImage follows the release channel");
        assertEquals("latest-snapshot", out.get("snapshotTag"));
        assertEquals("true", out.get("mirrorToSnapshot"), "a release must reach snapshot installs too");
    }

    /**
     * A maintenance patch cut after a newer line shipped (2.0.86 once 2.1 is out) must not pull either
     * channel's users back onto the older line, so it publishes only under its own permanent tag.
     */
    @Test
    void anOlderLinesPatchClaimsNeitherChannel() throws Exception {
        var out = run("refs/tags/v2.0.86", "v2.1.0");
        assertEquals("true", out.get("isRelease"));
        assertEquals("false", out.get("isNewest"), "it must not take the Latest badge from the newer line");
        assertEquals("v2.0.86", out.get("zsyncTag"), "it may only ever self-update to itself");
        assertEquals("false", out.get("mirrorToSnapshot"));
    }

    @Test
    void mainFeedsTheSnapshotChannel() throws Exception {
        var out = run("refs/heads/main", "v2.0.88");
        assertEquals("false", out.get("isRelease"));
        assertEquals("", out.get("releaseTag"));
        assertEquals("latest-snapshot", out.get("snapshotTag"));
        assertEquals("latest-snapshot", out.get("zsyncTag"));
        assertEquals("false", out.get("mirrorToSnapshot"));
    }

    /** The development line is whichever of main / releases/** is being built; both feed one channel. */
    @Test
    void maintenanceBranchFeedsTheSameSnapshotChannel() throws Exception {
        var out = run("refs/heads/releases/2.0", "v2.0.88");
        assertEquals("latest-snapshot", out.get("snapshotTag"));
        assertEquals("latest-snapshot", out.get("zsyncTag"));
    }

    /**
     * A test build of a feature branch stays self-contained: it publishes to its own rolling
     * pre-release and its AppImage follows that, so it cannot take over the channel real users follow.
     */
    @Test
    void aFeatureBranchOwnsItsSelfContainedTag() throws Exception {
        var out = run("refs/heads/copilot/some-feature", "v2.0.88");
        assertEquals("false", out.get("isRelease"));
        assertEquals("latest-copilot-some-feature", out.get("snapshotTag"));
        assertEquals("latest-copilot-some-feature", out.get("zsyncTag"));
        assertEquals("false", out.get("mirrorToSnapshot"));
    }

    /**
     * CI publishes rolling snapshots under {@code latest-*} tags. Those must never be mistaken for a
     * release, or every snapshot publish would re-trigger a stable build of itself.
     */
    @Test
    void aRollingSnapshotTagIsNotARelease() throws Exception {
        assertEquals("false", run("refs/tags/latest-snapshot", "v2.0.88").get("isRelease"));
    }
}
