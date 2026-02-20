package com.getpcpanel.cpp.linux.pulseaudio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SndCtrlPulseAudioTest {

    @CsvSource({
            "0,front-left: 0 /   0% / -inf dB,   front-right: 0 /   0% / -inf dB",
            "1,mono: 65536 / 100% / 0.00 dB",
            "0.64352417,front-left: 42174 /  64% / -11.49 dB,   front-right: 42174 /  64% / -11.49 dB",
    })
    @ParameterizedTest
    void extractVolume(float expected, String input) {
        var sut = new SndCtrlPulseAudio(null, null, null);

        var pa = PulseAudioWrapper.PulseAudioTarget.builder().metas(Map.of("Volume", input)).build();
        var volume = sut.extractVolume(pa);
        assertEquals(expected, volume);
    }
}
