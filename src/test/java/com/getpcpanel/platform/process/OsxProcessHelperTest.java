package com.getpcpanel.platform.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.OsxProcessHelper.FrontmostApp;

class OsxProcessHelperTest {
    @Test
    void parsesQuotedKeyValuePairs() {
        var app = OsxProcessHelper.parseFrontmostApp(List.of(
                "\"LSDisplayName\"=\"Stremio\"",
                "\"pid\"=50104",
                "\"CFBundleExecutablePath\"=\"/Applications/Stremio.app/Contents/MacOS/Stremio\""));

        assertEquals(new FrontmostApp(50104, "Stremio", "/Applications/Stremio.app/Contents/MacOS/Stremio"), app);
    }

    @Test
    void parsesQuotedKeyValuePairsOnOneLine() {
        var app = OsxProcessHelper.parseFrontmostApp(List.of(
                "\"LSDisplayName\"=\"Stremio\" \"pid\"=50104 \"CFBundleExecutablePath\"=\"/Applications/Stremio.app/Contents/MacOS/Stremio\""));

        assertEquals(new FrontmostApp(50104, "Stremio", "/Applications/Stremio.app/Contents/MacOS/Stremio"), app);
    }

    @Test
    void parsesHumanReadableInfoBlock() {
        var app = OsxProcessHelper.parseFrontmostApp(List.of(
                "\"Stremio\" ASN:0x0-0xe3ee3e: (in front) ",
                "    bundleID=\"com.stremio.stremio\"",
                "    bundle path=\"/Applications/Stremio.app\"",
                "    executable path=\"/Applications/Stremio.app/Contents/MacOS/Stremio\"",
                "    pid = 50104 !cgsConnection type=\"Foreground\" flavor=3 Version=\"5.1.28\" fileType=\"APPL\" creator=\"????\" Arch=ARM64 "));

        assertEquals(new FrontmostApp(50104, "Stremio", "/Applications/Stremio.app/Contents/MacOS/Stremio"), app);
    }

    @Test
    void nothingUsableIsNull() {
        assertNull(OsxProcessHelper.parseFrontmostApp(List.of()));
        assertNull(OsxProcessHelper.parseFrontmostApp(List.of("\"Stremio\" ASN:0x0-0xe3ee3e: (in front) ")));
    }
}
