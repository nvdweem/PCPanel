package com.getpcpanel.integration.keyboard.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;

@DisplayName("CommandMedia app-preference targeting")
class CommandMediaTest {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("legacy spotify=true saves migrate to the Spotify.exe app preference")
    void legacySpotifyFlagMigrates() throws Exception {
        var cmd = mapper.readValue("{\"_type\":\"keyboard.media\",\"button\":\"playPause\",\"spotify\":true}", CommandMedia.class);
        assertEquals(List.of("Spotify.exe"), cmd.getApps());
    }

    @Test
    @DisplayName("legacy spotify=false saves have no app preference (global media key)")
    void legacyGlobalStaysGlobal() throws Exception {
        var cmd = mapper.readValue("{\"_type\":\"keyboard.media\",\"button\":\"next\",\"spotify\":false}", CommandMedia.class);
        assertTrue(cmd.getApps().isEmpty());
    }

    @Test
    @DisplayName("an explicit app list is preserved in order and survives a round-trip")
    void appListRoundTrips() throws Exception {
        var original = new CommandMedia(VolumeButton.playPause, List.of("Spotify.exe", "chrome.exe"), false);
        var restored = mapper.readValue(mapper.writeValueAsString(original), CommandMedia.class);
        assertEquals(List.of("Spotify.exe", "chrome.exe"), restored.getApps());
    }

    @Test
    @DisplayName("an explicit app list wins over the legacy spotify flag")
    void explicitAppsWinOverLegacyFlag() throws Exception {
        var cmd = mapper.readValue("{\"_type\":\"keyboard.media\",\"button\":\"playPause\",\"apps\":[\"foobar.exe\"],\"spotify\":true}", CommandMedia.class);
        assertEquals(List.of("foobar.exe"), cmd.getApps());
    }

    @Test
    @DisplayName("the label names the targeted apps without the .exe suffix")
    void labelNamesTargets() {
        assertEquals("playPause", new CommandMedia(VolumeButton.playPause, List.of(), false).buildLabel());
        assertEquals("next (Spotify, chrome)", new CommandMedia(VolumeButton.next, List.of("Spotify.exe", "chrome.exe"), false).buildLabel());
    }
}
