package com.getpcpanel.device.lightshow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;

class AnimationsTest {
    private static final Layout PRO = new Layout(5, 4);

    @Test
    void framesCoverEveryLight() {
        var frame = Animations.frame(PRO, p -> "#123456");
        assertEquals(LightingMode.CUSTOM, frame.lightingMode());
        assertEquals(5, frame.knobConfigs().length);
        assertEquals(4, frame.sliderConfigs().length);
        assertEquals("#123456", frame.logoConfig().getColor());
    }

    @Test
    void ignitionStartsAndEndsDark() {
        var ignition = Animations.ignition();
        assertEquals("#000000", ignition.frame(0, PRO).knobConfigs()[4].getColor1(), "the spark has not reached K5 yet");
        assertEquals("#000000", ignition.frame(1, PRO).knobConfigs()[0].getColor1(), "faded out at the end");
        assertNotEquals("#000000", ignition.frame(0.6, PRO).knobConfigs()[0].getColor1(), "lit while the show runs");
    }

    @Test
    void selfTestShowsTheColoursThenEachLightAlone() {
        var test = Animations.selfTest();
        assertEquals("#FF0000", test.frame(0.01, PRO).knobConfigs()[0].getColor1());
        assertEquals("#FFFFFF", test.frame(0.39, PRO).sliderConfigs()[3].getColor1());
        var chase = test.frame(0.41, PRO);
        assertEquals("#FFFFFF", chase.knobConfigs()[0].getColor1());
        assertEquals("#000000", chase.knobConfigs()[1].getColor1());
    }

    @Test
    void hsvPrimaries() {
        assertEquals("#FF0000", Animations.hsv(0, 1, 1));
        assertEquals("#FFFFFF", Animations.hsv(0.5, 0, 1));
        assertEquals("#000000", Animations.hsv(0.3, 1, 0));
    }
}
