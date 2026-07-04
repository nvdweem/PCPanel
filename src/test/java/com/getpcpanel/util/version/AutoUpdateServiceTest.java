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
        // Not the installer: the Linux artifacts, or a checksum file.
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-1.8.123.AppImage").matches());
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("pcpanel_1.8.123_amd64.deb").matches());
        assertFalse(AutoUpdateService.SETUP_ASSET.matcher("PCPanel-setup.exe.sha256").matches());
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
