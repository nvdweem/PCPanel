package com.getpcpanel.util.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.util.version.Version.SemVer;

class VersionTest {
    // Snapshots are pre-releases of the version they lead to, so they sort BELOW that release
    // (SemVer 2.0.0 precedence: 2.1.0-snapshot.5 < 2.1.0). This is the whole point of the scheme:
    // a clean ".0" release must never be ranked below its own pre-release builds.
    @Test
    void snapshotsSortBelowTheReleaseTheyLeadTo() {
        var order = List.of(
                SemVer.fromName("v2.0-SNAPSHOT Pre-Release (82)"),
                SemVer.fromName("v2.0-SNAPSHOT Pre-Release (90)"),
                SemVer.fromName("v2.0"),
                SemVer.fromName("v2.1-SNAPSHOT Pre-Release (1)"),
                SemVer.fromName("v2.1-SNAPSHOT Pre-Release (5)"),
                SemVer.fromName("v2.1"));

        assertSortsTo(order);
    }

    // A bare release outranks its snapshot no matter how high the snapshot's build number climbs.
    @Test
    void releaseOutranksItsSnapshotRegardlessOfBuildNumber() {
        assertTrue(SemVer.fromName("v2.0-SNAPSHOT Pre-Release (999)").compareTo(SemVer.fromName("v2.0")) < 0);
        assertTrue(SemVer.fromName("v2.0").compareTo(SemVer.fromName("v2.0-SNAPSHOT Pre-Release (999)")) > 0);
    }

    // Within one pre-release line, the build number orders them.
    @Test
    void higherSnapshotBuildIsNewer() {
        assertTrue(SemVer.fromName("v2.1-SNAPSHOT Pre-Release (5)").compareTo(SemVer.fromName("v2.1-SNAPSHOT Pre-Release (40)")) < 0);
    }

    // The next line's snapshot is already newer than the previous final release.
    @Test
    void nextSnapshotOutranksPreviousRelease() {
        assertTrue(SemVer.fromName("v2.0").compareTo(SemVer.fromName("v2.1-SNAPSHOT Pre-Release (1)")) < 0);
    }

    // Missing components are treated as zero, so a patch (and its snapshot) sits above the minor.
    @Test
    void patchAndItsSnapshotSortAboveTheMinor() {
        var order = List.of(
                SemVer.fromName("1.7-SNAPSHOT (3)"),
                SemVer.fromName("1.7"),
                SemVer.fromName("1.7.1-SNAPSHOT (2)"),
                SemVer.fromName("1.7.1"),
                SemVer.fromName("1.8-SNAPSHOT (1)"),
                SemVer.fromName("1.8"));

        assertSortsTo(order);
    }

    private static void assertSortsTo(List<SemVer> ascending) {
        var shuffled = new ArrayList<>(ascending);
        shuffled.sort(Comparator.reverseOrder()); // start from a wrong order
        shuffled.sort(SemVer::compareTo);
        assertEquals(ascending, shuffled);
    }
}
