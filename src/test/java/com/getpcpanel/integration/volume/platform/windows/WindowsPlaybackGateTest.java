package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioSession;

class WindowsPlaybackGateTest {
    private static final String GAME_OUT = "{game}";
    private static final String MUSIC_OUT = "{music}";
    private static final String SPEAKERS = "{speakers}";

    private static AudioSession session(int pid, String exe) {
        return new AudioSession(null, pid, new File("C:\\Apps\\" + exe), exe, null, 1f, false);
    }

    @Test
    void anAppPlaysOnTheOutputWhereItIsLoudEvenWithSilentSessionsElsewhere() {
        // A game opens a session on every output and plays on one of them (Wave Link's Game output).
        var game = session(10, "Game.exe");
        var playing = WindowsPlaybackGate.playing(List.of(game), Map.of(
                "10|" + SPEAKERS, 0f,
                "10|" + MUSIC_OUT, 0f,
                "10|" + GAME_OUT, 0.34f));

        assertTrue(playing.any(List.of()));
        assertTrue(playing.any(List.of("Game.exe")));
        assertEquals(GAME_OUT, playing.device(List.of()));
    }

    @Test
    void theOutputIsTheLoudestMatchingAppsOne() {
        var game = session(10, "Game.exe");
        var music = session(20, "Music.exe");
        var playing = WindowsPlaybackGate.playing(List.of(game, music), Map.of(
                "10|" + GAME_OUT, 0.2f,
                "20|" + MUSIC_OUT, 0.6f));

        assertEquals(MUSIC_OUT, playing.device(List.of()));
        assertEquals(GAME_OUT, playing.device(List.of("Game.exe")));
    }

    @Test
    void silentAppsDontPlay() {
        var game = session(10, "Game.exe");
        var playing = WindowsPlaybackGate.playing(List.of(game), Map.of("10|" + GAME_OUT, 0.001f, "10|" + SPEAKERS, 0f));

        assertFalse(playing.any(List.of()));
        assertNull(playing.device(List.of()));
    }

    @Test
    void onlyTheCandidatesCount() {
        // A muted app or System Sounds is not a candidate, however loud its meter.
        var playing = WindowsPlaybackGate.playing(List.of(), Map.of("10|" + GAME_OUT, 0.9f));

        assertFalse(playing.any(List.of()));
    }

    @Test
    void anOutputOrInputHasSoundWhenItsOwnMeterShowsIt() {
        var asked = new java.util.ArrayList<String>();
        var playing = WindowsPlaybackGate.playing(List.of(), Map.of(), (id, input) -> {
            asked.add((input ? "in:" : "out:") + id);
            return switch ((input ? "in:" : "out:") + id) {
                case "out:null" -> 0.2f; // the default output
                case "in:{mic}" -> 0.05f;
                case "in:null" -> 0.001f;
                default -> -1f; // can't be read
            };
        });

        assertTrue(playing.output(null));
        assertFalse(playing.output(SPEAKERS));
        assertTrue(playing.input("{mic}"));
        assertFalse(playing.input(null), "below the threshold is silence");
        assertFalse(playing.any(List.of()), "no app plays");
        playing.output(null);
        assertEquals(4, asked.size(), "each endpoint is read once per check");
    }
}
