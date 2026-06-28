package com.getpcpanel.integration.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.FileUtil;

import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;

/**
 * The focused-app → Wave Link channel resolution that decides whether the focus-volume dial defers to
 * Wave Link or controls the OS volume. The key case: Wave Link names an app by a friendly name
 * ("Microsoft Edge") that doesn't match its executable ("msedge.exe"), and control must switch the
 * instant the app is added to / removed from a channel.
 */
class WaveLinkFocusResolutionTest {
    private static WaveLinkChannel channel(String id, String name, WaveLinkApp... apps) {
        return new WaveLinkChannel(id, name, null, null, null, false, List.of(apps), null, null);
    }

    /** A connected WaveLinkService whose channel map the test can mutate to simulate add/remove. */
    private static WaveLinkService connectedWith(Map<String, WaveLinkChannel> channels) {
        return new WaveLinkService() {
            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public Map<String, WaveLinkChannel> getChannels() {
                return channels;
            }
        };
    }

    @Test
    void defersWhenInChannel_evenWhenWaveLinkNameDiffersFromExe() {
        var channels = new LinkedHashMap<String, WaveLinkChannel>();
        var wl = connectedWith(channels);
        var edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        // Wave Link reports "Microsoft Edge" focused while the OS foreground is msedge.exe.
        wl.learnFocusIdentity(new WaveLinkApp("Microsoft Edge", "Microsoft Edge"), edgePath);

        // Not in any channel yet → the OS controls it.
        assertFalse(wl.controlsFocusApp(edgePath));

        // Added to a channel → defers to Wave Link immediately (resolved against the live channels).
        channels.put("browsers", channel("browsers", "Browsers", new WaveLinkApp("Microsoft Edge", "Microsoft Edge")));
        assertTrue(wl.controlsFocusApp(edgePath));

        // Removed again → back to the OS immediately.
        channels.clear();
        assertFalse(wl.controlsFocusApp(edgePath));
    }

    private static WaveLinkAppCache cacheIn(Path dir) {
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
    void defersViaPersistedIdentity_withoutLearningThisSession(@TempDir Path dir) {
        var edge = new WaveLinkApp("Microsoft Edge", "Microsoft Edge");
        var edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        // A previous session persisted the msedge.exe → "Microsoft Edge" pairing.
        cacheIn(dir).learnIdentity(edgePath, edge);

        // Fresh session: cache reloaded from disk, Wave Link connected with Edge in a channel, but no live
        // focus change has been correlated yet this session (the in-memory identity map starts empty).
        var cache = cacheIn(dir);
        cache.load();
        var channels = new LinkedHashMap<String, WaveLinkChannel>();
        channels.put("browsers", channel("browsers", "Browsers", edge));
        var wl = connectedWith(channels);
        wl.appCache = cache;

        assertTrue(wl.managesFocusApp(edgePath), "persisted identity bridges msedge.exe to its live channel");
        assertTrue(wl.controlsFocusApp(edgePath), "redirect resolves the channel from launch, before any focus change");

        // Still resolved live: removing Edge from every channel stops control immediately.
        channels.clear();
        assertFalse(wl.managesFocusApp(edgePath));
    }

    @Test
    void nameForId_resolvesTargetDisplayName() {
        var channels = new LinkedHashMap<String, WaveLinkChannel>();
        channels.put("PCM_OUT_00_V_14_SD8", channel("PCM_OUT_00_V_14_SD8", "Browsers"));
        var wl = connectedWith(channels);
        // This is what makes the overlay/UI show "Browsers — Wave Link" instead of "Set Channel".
        assertEquals("Browsers", wl.nameForId("PCM_OUT_00_V_14_SD8"));
        assertNull(wl.nameForId("unknown-id"));
        assertNull(wl.nameForId(null));
    }

    @Test
    void matchesByExeNameWhenItEqualsTheWaveLinkName_withoutLearning() {
        var channels = new LinkedHashMap<String, WaveLinkChannel>();
        channels.put("browsers", channel("browsers", "Browsers", new WaveLinkApp("Firefox", "Firefox")));
        var wl = connectedWith(channels);
        // No learned identity, but the exe basename equals the Wave Link app name.
        assertTrue(wl.controlsFocusApp("C:\\Program Files\\Mozilla Firefox\\firefox.exe"));
        assertFalse(wl.controlsFocusApp("C:\\Windows\\System32\\notepad.exe"));
    }
}
