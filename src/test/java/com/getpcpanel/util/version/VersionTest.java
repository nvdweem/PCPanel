package com.getpcpanel.util.version;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class VersionTest {
    @Test
    void semVerOrder() {
        var v17 = new Version.SemVer(List.of(1, 7), "");
        var v171 = new Version.SemVer(List.of(1, 7, 1), "");
        var v171s = new Version.SemVer(List.of(1, 7, 1), "snapshot");
        var v17s = new Version.SemVer(List.of(1, 7), "snapshot");
        var v18 = new Version.SemVer(List.of(1, 8), "");
        var v18s = new Version.SemVer(List.of(1, 8), "snapshot");

        var sorted = new ArrayList<>(List.of(v17, v17s,v171, v171s, v18, v18s));
        sorted.sort(Version.SemVer::compareTo);

        assertEquals(
                List.of(v17, v17s, v171, v171s, v18, v18s),
                sorted
        );
    }
}
