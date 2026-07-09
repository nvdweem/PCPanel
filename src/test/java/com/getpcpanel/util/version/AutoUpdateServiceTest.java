package com.getpcpanel.util.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.util.version.Version.SemVer;

class AutoUpdateServiceTest {
    private static Version release(int id, String name, boolean prerelease) {
        return new Version(id, "https://example/" + id, name, prerelease);
    }

    @Test
    void setupAssetPatternMatchesTheCiArtifactOnly() {
        assertTrue(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-1.8.123-setup.exe").matches());
        assertTrue(AutoUpdateService.SETUP_ASSET.matcher("pcpanel-2.0-setup.exe").matches());
        assertTrue(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-2.0.71-setup.exe").matches()); // real latest-main asset name
        // Not the installer: the Linux artifacts, or a checksum file.
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-1.8.123.AppImage").matches());
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("pcpanel_1.8.123_amd64.deb").matches());
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-setup.exe.sha256").matches());
    }

    // Real release shapes from the repo: the rolling snapshot channel is one pre-release whose name
    // carries the build number, e.g. "v2.0-SNAPSHOT Pre-Release (71)", tagged latest-main.
    @Test
    void chooseTargetSnapshotAlwaysTakesNewestPreReleaseEvenWhenRunningANewerBuild() {
        var releases = new Version[] {
                release(2, "v2.0-SNAPSHOT Pre-Release (71)", true), // published rolling build
                release(1, "v1.7.1", false),
        };
        // Running build 73 (newer than the published 71): reinstall-current must still resolve, to 71,
        // instead of demanding a non-existent "build 73" release.
        var current = SemVer.fromName("2.0-SNAPSHOT").withBuild(73);
        assertEquals(2, AutoUpdateService.chooseTarget(releases, false, true, true, current, 73).id());
        assertEquals(2, AutoUpdateService.chooseTarget(releases, true, true, true, current, 73).id());
    }

    @Test
    void chooseTargetReleaseBuildReinstallsTheExactCurrentVersion() {
        var releases = new Version[] {
                release(3, "v2.1", false),
                release(2, "v2.0", false),
        };
        assertEquals(2, AutoUpdateService.chooseTarget(releases, false, false, false, SemVer.fromName("2.0"), -1).id());
        // ...but "update to latest" jumps to the newest stable.
        assertEquals(3, AutoUpdateService.chooseTarget(releases, true, false, false, SemVer.fromName("2.0"), -1).id());
    }

    @Test
    void chooseTargetPreReleaseOptInControlsWhetherUpdatesSeeSnapshots() {
        var releases = new Version[] {
                release(3, "v2.1-SNAPSHOT Pre-Release (10)", true),
                release(2, "v2.0", false),
        };
        // Opted in: update jumps to the newer pre-release.
        assertEquals(3, AutoUpdateService.chooseTarget(releases, true, true, false, SemVer.fromName("2.0"), -1).id());
        // Opted out: only stable is considered, so it stays on 2.0.
        assertEquals(2, AutoUpdateService.chooseTarget(releases, true, false, false, SemVer.fromName("2.0"), -1).id());
    }

    @Test
    void selectLatestStableSkipsPreReleasesForAReleaseBuild() {
        var releases = new Version[] {
                release(3, "2.1 (30)", true),   // newest, but a pre-release
                release(2, "2.0", false),
                release(1, "1.9", false),
        };
        assertEquals(2, AutoUpdateService.selectLatest(releases, false).id());
    }

    @Test
    void selectLatestIncludesPreReleasesForASnapshotBuild() {
        var releases = new Version[] {
                release(3, "2.1 (30)", true),
                release(2, "2.0", false),
        };
        assertEquals(3, AutoUpdateService.selectLatest(releases, true).id());
    }

    @Test
    void selectCurrentMatchesTheRunningReleaseVersion() {
        var releases = new Version[] {
                release(2, "2.0", false),
                release(1, "1.9", false),
        };
        assertEquals(1, AutoUpdateService.selectCurrent(releases, SemVer.fromName("1.9"), false, -1).id());
    }

    @Test
    void selectCurrentMatchesTheBuildNumberForASnapshot() {
        var releases = new Version[] {
                release(3, "2.0 (31)", true),
                release(2, "2.0 (30)", true),
        };
        assertEquals(2, AutoUpdateService.selectCurrent(releases, SemVer.fromName("2.0").withBuild(30), true, 30).id());
    }

    @Test
    void selectCurrentReturnsNullWhenNoReleaseMatches() {
        var releases = new Version[] { release(2, "2.0", false) };
        assertNull(AutoUpdateService.selectCurrent(releases, SemVer.fromName("1.5"), false, -1));
    }
}
