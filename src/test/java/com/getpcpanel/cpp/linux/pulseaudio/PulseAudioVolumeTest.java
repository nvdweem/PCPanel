package com.getpcpanel.cpp.linux.pulseaudio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests the pure float<->PulseAudio-integer volume conversion in {@link PulseAudioWrapper}.
 * PulseAudio expresses volume as an integer where 65536 == 100%; these helpers translate to/from
 * the app's 0.0-1.0 float. No PulseAudio daemon needed — this is arithmetic.
 */
@DisplayName("PulseAudio volume conversion")
class PulseAudioVolumeTest {

    @ParameterizedTest
    @CsvSource({ "0.0, 0", "1.0, 65536", "0.5, 32768", "0.25, 16384" })
    @DisplayName("volumeFtoI maps the 0.0-1.0 float onto the 0-65536 PulseAudio scale")
    void floatToInt(float in, int expected) {
        assertEquals(expected, PulseAudioWrapper.volumeFtoI(in));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0.0", "65536, 1.0", "32768, 0.5" })
    @DisplayName("volumeItoF is the inverse mapping")
    void intToFloat(int in, float expected) {
        assertEquals(expected, PulseAudioWrapper.volumeItoF(in), 1e-6);
    }

    @Test
    @DisplayName("round-trips back to the original float within rounding")
    void roundTrips() {
        for (var v = 0f; v <= 1f; v += 0.1f) {
            assertEquals(v, PulseAudioWrapper.volumeItoF(PulseAudioWrapper.volumeFtoI(v)), 1e-4);
        }
    }
}
