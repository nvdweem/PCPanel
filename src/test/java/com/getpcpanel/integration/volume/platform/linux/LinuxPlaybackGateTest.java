package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.ISndCtrl;

class LinuxPlaybackGateTest {
    private static PulseAudioAudioSession stream(String exe, String sink) {
        return new PulseAudioAudioSession(null, 1, 10, new File("/usr/bin/" + exe), exe, null, 1f, false, null, sink);
    }

    private static PulseAudioAudioDevice device(String id, boolean output, boolean muted) {
        var d = new PulseAudioAudioDevice(null, 1, id, id, false, output);
        d.state(1f, muted);
        return d;
    }

    @Test
    void anOutputHasSoundWhileAStreamPlaysOnIt() {
        var snd = mock(ISndCtrl.class);
        when(snd.defaultPlayer()).thenReturn("speakers");
        var playing = LinuxPlaybackGate.playing(List.of(stream("spotify", "headset")), snd);

        assertTrue(playing.output("headset"));
        assertFalse(playing.output("speakers"));
        assertFalse(playing.output(null), "the default output is the speakers");
        assertEquals("headset", playing.device(List.of()));
    }

    @Test
    void anInputCountsAsHavingSoundWhileItIsThereAndUnmuted() {
        var snd = mock(ISndCtrl.class);
        when(snd.defaultRecorder()).thenReturn("mic");
        when(snd.getDevice("mic")).thenReturn(device("mic", false, false));
        when(snd.getDevice("muted-mic")).thenReturn(device("muted-mic", false, true));
        when(snd.getDevice("speakers")).thenReturn(device("speakers", true, false));
        var playing = LinuxPlaybackGate.playing(List.of(), snd);

        assertTrue(playing.input(null));
        assertTrue(playing.input("mic"));
        assertFalse(playing.input("muted-mic"));
        assertFalse(playing.input("speakers"), "an output is not an input");
        assertFalse(playing.input("gone"));
        assertFalse(playing.any(List.of()));
    }
}
