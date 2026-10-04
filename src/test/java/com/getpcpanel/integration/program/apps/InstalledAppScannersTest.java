package com.getpcpanel.integration.program.apps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstalledAppScannersTest {
    @TempDir Path dir;

    @Test
    void aStartMenuShortcutOpensItselfAndMatchesItsExe() {
        var lnk = Path.of("C:\\Start Menu\\Programs\\Spotify.lnk");

        var exe = "C:\\Users\\me\\AppData\\Roaming\\Spotify\\Spotify.exe";

        assertEquals(new InstalledApp("Spotify", lnk.toString(), "Spotify.exe", exe), WindowsInstalledApps.toApp(lnk, exe));
    }

    @Test
    void shortcutsToOtherThingsOrUninstallersAreSkipped() {
        assertNull(WindowsInstalledApps.toApp(Path.of("Readme.lnk"), "C:\\App\\readme.txt"));
        assertNull(WindowsInstalledApps.toApp(Path.of("Website.lnk"), "https://example.com"));
        assertNull(WindowsInstalledApps.toApp(Path.of("Unknown.lnk"), null));
        assertNull(WindowsInstalledApps.toApp(Path.of("Uninstall App.lnk"), "C:\\App\\unins000.exe"));
        assertNull(WindowsInstalledApps.toApp(Path.of("App Uninstaller.lnk"), "C:\\App\\remove.exe"));
    }

    @Test
    void storeAppsOpenByTheirAppId() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                [{"name":"Terminal","id":"Microsoft.WindowsTerminal_8wekyb3d8bbwe!App","exe":"C:\\\\Program Files\\\\WindowsApps\\\\Microsoft.WindowsTerminal_1.24_x64__8wekyb3d8bbwe\\\\WindowsTerminal.exe"},
                 {"name":"No program","id":"Some.App_123!App","exe":null},
                 {"name":"Uninstall Thing","id":"Thing_1!App","exe":"C:\\\\x\\\\thing.exe"}]
                """);
        assertEquals(List.of(new InstalledApp("Terminal", "shell:AppsFolder\\Microsoft.WindowsTerminal_8wekyb3d8bbwe!App", "WindowsTerminal.exe",
                "C:\\Program Files\\WindowsApps\\Microsoft.WindowsTerminal_1.24_x64__8wekyb3d8bbwe\\WindowsTerminal.exe")), WindowsInstalledApps.storeApps(json));
        assertEquals(List.of(), WindowsInstalledApps.storeApps(null));
    }

    @Test
    void startMenuShortcutsAreFoundInEveryFolder() throws IOException {
        Files.createDirectories(dir.resolve("Tools/Sub"));
        Files.writeString(dir.resolve("Top.lnk"), "x");
        Files.writeString(dir.resolve("Tools/Sub/Deep.LNK"), "x");
        Files.writeString(dir.resolve("Tools/readme.txt"), "x");

        assertEquals(List.of(dir.resolve("Tools/Sub/Deep.LNK"), dir.resolve("Top.lnk")),
                WindowsInstalledApps.shortcuts(dir).stream().sorted().toList());
    }

    @Test
    void anUnreadableStartMenuFolderDoesNotEndTheWalk() {
        assertEquals(FileVisitResult.CONTINUE,
                new WindowsInstalledApps.ShortcutCollector().visitFileFailed(dir.resolve("Locked"), new AccessDeniedException("Locked")));
    }

    @Test
    void theFirstDesktopEntryOfAnIdWins() {
        var listing = List.of(
                LinuxInstalledApps.RECORD + "/home/me/.local/share/applications/firefox.desktop",
                "[Desktop Entry]", "Name=Firefox", "Exec=firefox", "Hidden=true",
                LinuxInstalledApps.RECORD + "/home/me/.local/share/applications/mine.desktop",
                "[Desktop Entry]", "Name=Mine", "Exec=/opt/mine/run",
                LinuxInstalledApps.RECORD + "/usr/share/applications/firefox.desktop",
                "[Desktop Entry]", "Name=Firefox", "Exec=firefox %u",
                LinuxInstalledApps.RECORD + "/usr/share/applications/mine.desktop",
                "[Desktop Entry]", "Name=Mine (system)", "Exec=mine",
                LinuxInstalledApps.RECORD + "/usr/share/applications/gimp.desktop",
                "[Desktop Entry]", "Name=GIMP", "Exec=gimp-2.10 %U", "StartupWMClass=gimp");

        assertEquals(List.of(
                new InstalledApp("Mine", "/home/me/.local/share/applications/mine.desktop", "run"),
                new InstalledApp("GIMP", "/usr/share/applications/gimp.desktop", "gimp")),
                LinuxInstalledApps.fromListing(listing));
    }

    @Test
    void macAppsAreTheAppBundles() throws IOException {
        Files.createDirectories(dir.resolve("Safari.app/Contents/MacOS"));
        Files.createDirectories(dir.resolve("Utilities/Terminal.app"));
        Files.createDirectories(dir.resolve("Safari.app/Contents/Helpers/Inner.app"));
        Files.createDirectories(dir.resolve("Not An App"));
        Files.writeString(dir.resolve("file.txt"), "x");

        var apps = MacInstalledApps.scan(List.of(dir, dir.resolve("missing")));

        assertEquals(List.of(
                new InstalledApp("Safari", dir.resolve("Safari.app").toString(), "Safari"),
                new InstalledApp("Terminal", dir.resolve("Utilities/Terminal.app").toString(), "Terminal")),
                apps.stream().sorted((a, b) -> a.name().compareTo(b.name())).toList());
    }

    @Test
    void theListIsSortedByNameWithoutDuplicates() {
        var apps = List.of(
                new InstalledApp("spotify", "C:\\user\\Spotify.lnk", "Spotify.exe"),
                new InstalledApp("Audacity", "C:\\all\\Audacity.lnk", "audacity.exe"),
                new InstalledApp("Spotify", "C:\\all\\Spotify.lnk", "spotify.exe"),
                new InstalledApp("Spotify", "C:\\all\\Spotify Beta.lnk", "SpotifyBeta.exe"));

        assertEquals(List.of(
                new InstalledApp("Audacity", "C:\\all\\Audacity.lnk", "audacity.exe"),
                new InstalledApp("spotify", "C:\\user\\Spotify.lnk", "Spotify.exe"),
                new InstalledApp("Spotify", "C:\\all\\Spotify Beta.lnk", "SpotifyBeta.exe")),
                InstalledApps.tidy(apps));
    }
}
