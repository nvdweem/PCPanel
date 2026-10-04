package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowsAudioLevelMeterTest {
    @Test
    void aDevicesOwnVolumeScalesItsPeak() {
        // Measured on Wave Link's Music device: the meter reads -3.5 dB with the device at -34.9 dB, which is what
        // the mix it feeds (Digital Output, at 0 dB) reads.
        assertEquals(-38.4, 20 * Math.log10(WindowsAudioLevelMeter.audible(0.672f, -34.9f, false)), 0.1);
        assertEquals(0.5f, WindowsAudioLevelMeter.audible(0.5f, 0f, false), 1e-6);
    }

    @Test
    void aMutedDeviceIsSilent() {
        assertEquals(0f, WindowsAudioLevelMeter.audible(0.9f, 0f, true));
    }
}
