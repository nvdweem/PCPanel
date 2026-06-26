package com.getpcpanel.cpp.linux.pulseaudio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.util.ProcessHelper;

class PulseAudioWrapperTest {

    /**
     * Regression for the Linux native build crashing on startup with "Cannot run program pactl": a missing
     * pactl (no PulseAudio/PipeWire - a headless box or a CI runner without pulseaudio-utils) must degrade to
     * a no-op ISndCtrl, not abort. Reads return empty (so startup's device/session enumeration succeeds and
     * the app serves the UI) and writes don't throw.
     */
    @Test
    void degradesToNoOpWhenPactlIsUnavailable() {
        var sut = new PulseAudioWrapper();
        // Redirect every invocation to a guaranteed-missing binary so start() throws "command not found",
        // exactly as a real machine without pactl would.
        sut.processHelper = new ProcessHelper() {
            @Override
            public ProcessBuilder builder(String... command) {
                return super.builder("pcpanel-no-such-binary");
            }
        };

        assertEquals(List.of(), sut.devices(), "no devices should be reported when pactl is unavailable");
        assertEquals(List.of(), sut.getSessions(), "no sessions should be reported when pactl is unavailable");
        assertDoesNotThrow(() -> sut.setSessionVolume(0, 0.5f), "a volume write must not throw");
        assertDoesNotThrow(() -> sut.setDeviceVolume(true, 0, 0.5f), "a device volume write must not throw");
        assertDoesNotThrow(() -> sut.muteSession(0, MuteType.mute), "a mute write must not throw");
        assertDoesNotThrow(() -> sut.setDefaultDevice(true, 0), "setting the default device must not throw");
    }
}
