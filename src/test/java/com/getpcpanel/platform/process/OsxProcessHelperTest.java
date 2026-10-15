package com.getpcpanel.platform.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.OsxProcessHelper.FrontmostApp;
import com.getpcpanel.util.os.ProcessHelper;

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

        assertEquals(new FrontmostApp(50104, "Stremio", "/Applications/Stremio.app/Contents/MacOS/Stremio", "com.stremio.stremio"), app);
    }

    @Test
    void nothingUsableIsNull() {
        assertNull(OsxProcessHelper.parseFrontmostApp(List.of()));
        assertNull(OsxProcessHelper.parseFrontmostApp(List.of("\"Stremio\" ASN:0x0-0xe3ee3e: (in front) ")));
    }

    @Test
    @DisplayName("listApps parses conventional applications and Safari coalitions")
    void parsesConventionalAndSafariCoalitions() throws Exception {
        var processHelper = mock(ProcessHelper.class);
        var lines = List.of(
            "1) \"Spotify\" ASN:0x0-0x11011:",
            "    bundleID=\"com.spotify.client\"",
            "    bundle path=\"/Applications/Spotify.app\"",
            "    executable path=\"/Applications/Spotify.app/Contents/MacOS/Spotify\"",
            "    pid = 12345 type=\"Foreground\"",
            "    coalition: 5000 { 12345 12346 }",
            "2) \"Safari\" ASN:0x0-0x22022:",
            "    bundleID=\"com.apple.Safari\"",
            "    bundle path=\"/Applications/Safari.app\"",
            "    executable path=\"/Applications/Safari.app/Contents/MacOS/Safari\"",
            "    pid = 31430 type=\"Foreground\"",
            "    coalition: 11761  { 31430 31439 31452 31454 31460 }",
            "3) \"Safari Graphics and Media\" ASN:0x0-0x22023:",
            "    bundleID=\"com.apple.WebKit.GPU\"",
            "    executable path=\"/System/Library/Frameworks/WebKit.framework/Versions/A/XPCServices/com.apple.WebKit.GPU.xpc/Contents/MacOS/com.apple.WebKit.GPU\"",
            "    pid = 31452 type=\"UIElement\""
        );

        when(processHelper.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("list")))
            .thenReturn(new ProcessHelper.Result(false, 0, lines, List.of()));

        var osxHelper = new OsxProcessHelper(processHelper);
        var apps = osxHelper.listApps();

        assertEquals(3, apps.size());

        var spotify = apps.get(0);
        assertEquals(12345, spotify.pid());
        assertEquals("Spotify", spotify.name());
        assertEquals("/Applications/Spotify.app/Contents/MacOS/Spotify", spotify.executablePath());
        assertTrue(spotify.foreground());
        assertEquals(Set.of(12345, 12346), spotify.coalitionPids());

        var safari = apps.get(1);
        assertEquals(31430, safari.pid());
        assertEquals("Safari", safari.name());
        assertEquals("/Applications/Safari.app/Contents/MacOS/Safari", safari.executablePath());
        assertTrue(safari.foreground());
        assertEquals(Set.of(31430, 31439, 31452, 31454, 31460), safari.coalitionPids());

        var gpuHelper = apps.get(2);
        assertEquals(31452, gpuHelper.pid());
        assertEquals("Safari Graphics and Media", gpuHelper.name());
        assertFalse(gpuHelper.foreground());
        assertTrue(gpuHelper.coalitionPids().isEmpty());
    }

    @Test
    @DisplayName("listApps parses Safari-installed PWA with Web App executable and coalition")
    void parsesPwaWithWebAppExecutableAndCoalition() throws Exception {
        var processHelper = mock(ProcessHelper.class);
        var lines = List.of(
            "1) \"YT Music\" ASN:0x0-0x33033:",
            "    bundleID=\"com.apple.Safari.WebApp.D20677F6-5596-4F70-BE3A-A6DFDE29ECB5\"",
            "    bundle path=\"/Users/user/Applications/YT Music.app\"",
            "    executable path=\"/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App\"",
            "    pid = 64765 type=\"Foreground\"",
            "    coalition: 20488  { 64765 64772 }",
            "2) \"YT Music Graphics and Media\" ASN:0x0-0x33034:",
            "    bundleID=\"com.apple.WebKit.GPU\"",
            "    executable path=\"/System/Library/Frameworks/WebKit.framework/Versions/A/XPCServices/com.apple.WebKit.GPU.xpc/Contents/MacOS/com.apple.WebKit.GPU\"",
            "    pid = 64772 type=\"UIElement\""
        );

        when(processHelper.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("list")))
            .thenReturn(new ProcessHelper.Result(false, 0, lines, List.of()));

        var osxHelper = new OsxProcessHelper(processHelper);
        var apps = osxHelper.listApps();

        assertEquals(2, apps.size());

        var pwa = apps.get(0);
        assertEquals(64765, pwa.pid());
        assertEquals("YT Music", pwa.name());
        assertEquals("/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App", pwa.executablePath());
        assertEquals("com.apple.Safari.WebApp.D20677F6-5596-4F70-BE3A-A6DFDE29ECB5", pwa.bundleId());
        assertTrue(pwa.foreground());
        assertEquals(Set.of(64765, 64772), pwa.coalitionPids());
    }

    @Test
    @DisplayName("listApps handles missing or unbracketed coalition lines gracefully")
    void handlesMissingCoalitionGracefully() throws Exception {
        var processHelper = mock(ProcessHelper.class);
        var lines = List.of(
            "1) \"Calculator\" ASN:0x0-0x44044:",
            "    executable path=\"/System/Applications/Calculator.app/Contents/MacOS/Calculator\"",
            "    pid = 9999 type=\"Foreground\"",
            "    coalition: 8888",
            "2) \"Notes\" ASN:0x0-0x44045:",
            "    executable path=\"/System/Applications/Notes.app/Contents/MacOS/Notes\"",
            "    pid = 10000 type=\"Foreground\""
        );

        when(processHelper.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("list")))
            .thenReturn(new ProcessHelper.Result(false, 0, lines, List.of()));

        var osxHelper = new OsxProcessHelper(processHelper);
        var apps = osxHelper.listApps();

        assertEquals(2, apps.size());
        assertEquals(Set.of(), apps.get(0).coalitionPids());
        assertEquals(Set.of(), apps.get(1).coalitionPids());
    }

    @Test
    @DisplayName("rapid focus switching is fresh for lookup and foreground PID consumers")
    void rapidFocusSwitchingIsFresh() throws Exception {
        var processes = mock(ProcessHelper.class);
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("front")))
            .thenReturn(new ProcessHelper.Result(false, 0, List.of("ASN:A"), List.of()),
                        new ProcessHelper.Result(false, 0, List.of("ASN:B"), List.of()));
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("info"), eq("-only"), eq("name,pid,executablepath,bundleid"), eq("ASN:A")))
            .thenReturn(new ProcessHelper.Result(false, 0, List.of("\"pid\"=100"), List.of()));
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("info"), eq("-only"), eq("name,pid,executablepath,bundleid"), eq("ASN:B")))
            .thenReturn(new ProcessHelper.Result(false, 0, List.of("\"pid\"=200"), List.of()));
        var helper = new OsxProcessHelper(processes);
        assertEquals(100, helper.getFrontmostApp().pid());
        assertEquals(200, helper.foregroundPid().orElseThrow());
    }

    @Test
    @DisplayName("listApps is fresh across commands so coalitions cannot retain reused PIDs")
    void listAppsIsFresh() throws Exception {
        var processHelper = mock(ProcessHelper.class);
        var lines = List.of(
            "1) \"Safari\" ASN:0x0-0x111:",
            "    executable path=\"/Applications/Safari.app/Contents/MacOS/Safari\"",
            "    pid = 31430 type=\"Foreground\"",
            "    coalition: 500 { 31430 31452 }"
        );
        when(processHelper.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("list")))
            .thenReturn(new ProcessHelper.Result(false, 0, lines, List.of()),
                        new ProcessHelper.Result(false, 0, List.of(
                            "1) \"Safari\" ASN:0x0-0x111:",
                            "    executable path=\"/Applications/Safari.app/Contents/MacOS/Safari\"",
                            "    pid = 31430 type=\"Foreground\"",
                            "    coalition: 500 { 31430 31455 }"), List.of()));

        var osxHelper = new OsxProcessHelper(processHelper);
        var apps1 = osxHelper.listApps();
        var apps2 = osxHelper.listApps();

        assertEquals(Set.of(31430, 31452), apps1.getFirst().coalitionPids());
        assertEquals(Set.of(31430, 31455), apps2.getFirst().coalitionPids());
        org.mockito.Mockito.verify(processHelper, org.mockito.Mockito.times(2))
            .run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("list"));
    }
}
