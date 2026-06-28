package com.getpcpanel.integration.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.FileUtil;

import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;

class WaveLinkAppCacheTest {

    private WaveLinkAppCache cacheIn(Path dir) {
        var cache = new WaveLinkAppCache();
        cache.mapper = new ObjectMapper();
        cache.fileUtil = new FileUtil() {
            @Override
            public File getFile(String file) {
                return dir.resolve(file).toFile();
            }
        };
        return cache;
    }

    @Test
    void normalize_stripsDirectoryExtensionAndCase() {
        assertEquals("firefox", WaveLinkAppCache.normalize("C:\\Program Files\\Mozilla Firefox\\firefox.exe"));
        assertEquals("firefox", WaveLinkAppCache.normalize("Firefox")); // Wave Link app name (no extension)
        assertEquals("spotify", WaveLinkAppCache.normalize("/usr/bin/Spotify.AppImage"));
        assertEquals("microsoft edge", WaveLinkAppCache.normalize("Microsoft Edge")); // a multi-word name, no dot
        assertEquals("", WaveLinkAppCache.normalize(null));
        assertEquals("", WaveLinkAppCache.normalize("   "));
    }

    @Test
    void learn_bridgesFocusPathToBasename_caseAndPathInsensitive(@TempDir Path dir) {
        var cache = cacheIn(dir);
        assertFalse(cache.isControlled("C:\\x\\msedge.exe"));
        cache.learn("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        assertTrue(cache.isControlled("D:\\elsewhere\\MSEDGE.EXE"), "match is path- and case-insensitive");
        assertFalse(cache.isControlled("C:\\x\\chrome.exe"));
    }

    @Test
    void syncFromChannels_matchesFocusExeByName(@TempDir Path dir) {
        var cache = cacheIn(dir);
        var browsers = new WaveLinkChannel("c1", "Browsers", null, null, null, false,
                List.of(new WaveLinkApp("Firefox", "Firefox")), null, null);
        cache.syncFromChannels(List.of(browsers));
        // Wave Link names the app "Firefox"; the OS reports the focused window as "...\firefox.exe".
        assertTrue(cache.isControlled("C:\\Program Files\\Mozilla Firefox\\firefox.exe"));
        assertFalse(cache.isControlled("C:\\x\\notepad.exe"));
    }

    @Test
    void entriesArePersistedAndReloaded(@TempDir Path dir) {
        cacheIn(dir).learn("C:\\apps\\Spotify.exe");
        var reloaded = cacheIn(dir);
        reloaded.load(); // mimics @PostConstruct on a fresh process/session
        assertTrue(reloaded.isControlled("C:\\other\\spotify.exe"), "controlled set survives a restart");
    }

    @Test
    void identity_bridgesExeToWaveLinkApp_pathAndCaseInsensitive(@TempDir Path dir) {
        var cache = cacheIn(dir);
        var edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        assertNull(cache.identity(edgePath));
        cache.learnIdentity(edgePath, new WaveLinkApp("Microsoft Edge", "Microsoft Edge"));
        assertEquals("Microsoft Edge", cache.identity("D:\\elsewhere\\MSEDGE.EXE").name());
        assertNull(cache.identity("C:\\x\\chrome.exe"));
    }

    @Test
    void identity_survivesRestart(@TempDir Path dir) {
        var edge = new WaveLinkApp("Microsoft Edge", "Microsoft Edge");
        cacheIn(dir).learnIdentity("C:\\...\\msedge.exe", edge);
        var reloaded = cacheIn(dir);
        reloaded.load(); // mimics @PostConstruct on a fresh process/session
        // The exe→friendly-name pairing is available before any focus change of the new session, so the
        // focused app can be resolved to its live Wave Link channel from launch.
        assertEquals(edge, reloaded.identity("X:\\Edge\\msedge.exe"));
    }

    @Test
    void learnIdentity_ignoresBlankAndEmptyApp(@TempDir Path dir) {
        var cache = cacheIn(dir);
        cache.learnIdentity("   ", new WaveLinkApp("Microsoft Edge", "Microsoft Edge"));
        cache.learnIdentity("C:\\x\\msedge.exe", WaveLinkApp.EMPTY);
        assertNull(cache.identity("C:\\x\\msedge.exe"));
    }
}
