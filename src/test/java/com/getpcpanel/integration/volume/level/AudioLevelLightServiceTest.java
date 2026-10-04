package com.getpcpanel.integration.volume.level;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AudioLevelLightServiceTest {
    @Test
    void shapesPeaksOnADecibelScale() {
        assertEquals(0f, AudioLevelLightService.shape(0f));
        assertEquals(1f, AudioLevelLightService.shape(1f), 1e-4);
        assertEquals(0.5f, AudioLevelLightService.shape(0.0316228f), 1e-3, "-30 dB is halfway");
        assertEquals(0f, AudioLevelLightService.shape(1e-5f), "below the floor is dark");
    }

    @Test
    void blendsFromQuietToLoud() {
        assertEquals("#000000", AudioLevelLightService.blend(null, "#FF8000", 0));
        assertEquals("#FF8000", AudioLevelLightService.blend(null, "#FF8000", 1));
        assertEquals("#804000", AudioLevelLightService.blend("", "#FF8000", 0.5f));
        assertEquals("#0000FF", AudioLevelLightService.blend("#0000FF", "#FF0000", 0));
    }
}
