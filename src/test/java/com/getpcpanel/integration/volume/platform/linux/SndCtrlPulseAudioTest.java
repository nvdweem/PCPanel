package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.platform.process.LinuxProcessHelper.ActiveWindow;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;

class SndCtrlPulseAudioTest {

    @CsvSource(value = {
            "0;front-left: 0 /   0% / -inf dB,   front-right: 0 /   0% / -inf dB",
            "1;mono: 65536 / 100% / 0.00 dB",
            "0.64352417;front-left: 42174 /  64% / -11.49 dB,   front-right: 42174 /  64% / -11.49 dB",
    }, delimiter = ';')
    @ParameterizedTest
    void extractVolume(float expected, String input) {
        var sut = new SndCtrlPulseAudio();

        var pa = PulseAudioTarget.builder().metas(Map.of("Volume", input)).build();
        var volume = sut.extractVolume(pa);
        assertEquals(expected, volume);
    }

    /** A regular app exposes application.name / process.binary and is matched by either. */
    @Test
    void matchesByExecutableAndName() {
        var session = session(Map.of(
                "application.process.id", "1234",
                "application.process.binary", "firefox",
                "application.name", "Firefox"));

        assertEquals("Firefox", session.title());
        assertTrue(SndCtrlPulseAudio.matches(session, "firefox"), "should match the binary name");
        assertTrue(SndCtrlPulseAudio.matches(session, "Firefox"), "should match the application name");
        assertFalse(SndCtrlPulseAudio.matches(session, "chrome"));
    }

    /**
     * Proton/Wine games expose their stream as {@code <game>.exe}, but the focused window's title is just the game
     * name (e.g. window "Deadlock" vs stream "deadlock.exe"). Dropping a trailing .exe on both sides lets focus
     * volume bind them, regardless of case (#96).
     */
    @Test
    void matchesProtonGameByWindowNameIgnoringExeSuffix() {
        var session = session(Map.of(
                "application.process.id", "987",
                "application.process.binary", "deadlock.exe",
                "application.name", "deadlock.exe"));

        assertTrue(SndCtrlPulseAudio.matches(session, "Deadlock"), "the window name must match the .exe stream");
        assertTrue(SndCtrlPulseAudio.matches(session, "deadlock.exe"), "the raw .exe still matches");
        assertTrue(SndCtrlPulseAudio.matches(session, "DEADLOCK"), "match is case-insensitive");
        assertFalse(SndCtrlPulseAudio.matches(session, "deadlock2"), "a different name must not match");
    }

    /**
     * Real Deadlock (Proton) data: the window's process is "MainThrd" and the stream's binary is
     * "wine64-preloader", so no name lines up - but the stream's application.process.id equals the window pid.
     * The pid path must bind it, and must do so even if the title were decorated/unmatchable (#96).
     */
    @Test
    void matchesProtonGameByPidWhenNamesDoNotLineUp() {
        var deadlock = session(Map.of(
                "application.process.id", "1158977",
                "application.process.binary", "wine64-preloader",
                "application.name", "deadlock.exe"));

        // process "MainThrd" / class "steam_app_1422450" / name "Deadlock", as captured from the focused window.
        var window = new ActiveWindow(1158977, "MainThrd", null, "steam_app_1422450", "Deadlock");
        assertTrue(SndCtrlPulseAudio.matchesWindow(deadlock, window), "the window pid must bind the stream by application.process.id");

        // Even a window whose name does not match still binds when the pid agrees (decorated-title resilience).
        var decorated = new ActiveWindow(1158977, "MainThrd", null, "steam_app_1422450", "Deadlock - Main Menu");
        assertTrue(SndCtrlPulseAudio.matchesWindow(deadlock, decorated), "pid match is independent of the window title");

        // A different window (different pid, non-matching names) must not touch this stream.
        var other = new ActiveWindow(4242, "firefox", null, "firefox_firefox", "Some Page - Firefox");
        assertFalse(SndCtrlPulseAudio.matchesWindow(deadlock, other), "an unrelated window must not match");
    }

    /** Both pids must be real: a metadata-sparse stream (pid -1) must not match a window via a sentinel pid. */
    @Test
    void pidMatchRequiresRealPids() {
        var sparse = session(Map.of("media.name", "audio-src")); // no application.process.id -> pid -1
        var window = new ActiveWindow(-1, null, null, null, null);

        assertFalse(SndCtrlPulseAudio.matchesWindow(sparse, window), "sentinel pids (-1) must never collide");
    }

    /** A blank-once-normalized query (e.g. ".exe") must not match a metadata-sparse stream whose names are also blank. */
    @Test
    void emptyAfterStrippingExeNeverMatches() {
        var sparse = session(Map.of("media.name", "audio-src"));

        assertFalse(SndCtrlPulseAudio.matches(sparse, ".exe"), "an empty normalized query must not match a blank executable");
    }

    /** Spotify Flatpak >= 1.2.86 exposes only media.name + portal app id; it must stay targetable (#92). */
    @Test
    void matchesSpotifyByPortalAppIdWhenProcessMetadataMissing() {
        var session = session(Map.of(
                "media.name", "audio-src",
                "pipewire.access.portal.app_id", "com.spotify.Client"));

        assertEquals("com.spotify.Client", session.portalAppId());
        assertEquals("com.spotify.Client", session.title(), "portal app id should be the fallback title");
        assertTrue(SndCtrlPulseAudio.matches(session, "com.spotify.Client"), "should match the portal app id");
        assertTrue(SndCtrlPulseAudio.matches(session, "COM.SPOTIFY.CLIENT"), "match is case-insensitive");
        assertFalse(SndCtrlPulseAudio.matches(session, ""), "blank query never matches");
    }

    /** The app selector must show a bindable name; metadata-sparse sink-inputs fall back to title/portal id (#71). */
    @Test
    void runningAppNamePrefersExecutableThenTitle() {
        var firefox = session(Map.of("application.process.binary", "firefox", "application.name", "Firefox"));
        assertEquals("firefox", SndCtrlPulseAudio.runningAppName(firefox), "uses the executable name when present");

        var spotify = session(Map.of("media.name", "audio-src", "pipewire.access.portal.app_id", "com.spotify.Client"));
        assertEquals("com.spotify.Client", SndCtrlPulseAudio.runningAppName(spotify),
                "falls back to a non-blank, bindable name when no executable is exposed");
        assertTrue(SndCtrlPulseAudio.matches(spotify, SndCtrlPulseAudio.runningAppName(spotify)),
                "the selector name must be something matches() accepts, so binding works");

        var mediaOnly = session(Map.of("media.name", "Discord"));
        assertEquals("Discord", SndCtrlPulseAudio.runningAppName(mediaOnly), "uses media.name when that is all there is");
    }

    private static PulseAudioAudioSession session(Map<String, String> properties) {
        var pa = PulseAudioTarget.builder().properties(properties).metas(Map.of()).build();
        return new SndCtrlPulseAudio().toSession(pa);
    }
}
