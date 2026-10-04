package com.getpcpanel.device.lightshow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;

class SoftwareAnimationTest {
    private static final Layout PRO = new Layout(5, 4);

    private static List<String> colors(LightingConfig frame) {
        return Stream.concat(Stream.concat(Stream.of(frame.knobConfigs()).map(SingleKnobLightingConfig::getColor1),
                                     Stream.of(frame.sliderConfigs()).map(s -> s.getColor1())),
                             Stream.of(frame.logoConfig().getColor())).toList();
    }

    @Test
    void periodFollowsSpeed() {
        assertEquals(10_000, SoftwareAnimation.periodMs((byte) 0));
        assertEquals(10_000 - 100 * 37, SoftwareAnimation.periodMs((byte) 100));
        assertEquals(565, SoftwareAnimation.periodMs((byte) -1), "speed is unsigned: 255 is the fastest");
    }

    @Test
    void allColourLightsEverythingInThatColour() {
        var base = LightingConfig.createAllColor("#123456");
        base.setGlobalBrightness(40);
        var frame = SoftwareAnimation.frame(base, PRO, 1234);
        assertEquals(LightingMode.CUSTOM, frame.lightingMode());
        assertEquals(40, frame.getGlobalBrightness(), "global brightness is kept");
        for (var knob : frame.knobConfigs()) {
            assertEquals(SINGLE_KNOB_MODE.STATIC, knob.getMode());
            assertEquals("#123456", knob.getColor1());
        }
        for (var label : frame.sliderLabelConfigs()) {
            assertEquals("#123456", label.getColor());
        }
        assertTrue(colors(frame).stream().allMatch("#123456"::equals));
        assertEquals(LightingMode.ALL_COLOR, base.lightingMode(), "the base is left alone");
        assertEquals(0, base.knobConfigs().length, "the base is left alone");
    }

    @Test
    void volumeTrackingKnobsKeepFollowingTheVolume() {
        var base = LightingConfig.createAllColor("#123456").toBuilder().volumeBrightnessTrackingEnabled(new boolean[] { false, true }).build();
        var frame = SoftwareAnimation.frame(base, new Layout(4, 0), 0);
        assertEquals(SINGLE_KNOB_MODE.STATIC, frame.knobConfigs()[0].getMode());
        assertEquals(SINGLE_KNOB_MODE.VOLUME_GRADIENT, frame.knobConfigs()[1].getMode());
        assertEquals("#123456", frame.knobConfigs()[1].getColor2());
    }

    @Test
    void singleColourLightsEachLightInItsOwnColour() {
        var base = LightingConfig.createAllColor("#000000").toBuilder().lightingMode(LightingMode.SINGLE_COLOR)
                                 .individualColors(new String[] { "#110000", "#002200", "#000033", "#444444" }).build();
        var frame = SoftwareAnimation.frame(base, new Layout(4, 0), 0);
        assertEquals("#110000", frame.knobConfigs()[0].getColor1());
        assertEquals("#000033", frame.knobConfigs()[2].getColor1());
        assertEquals("#444444", frame.knobConfigs()[3].getColor1());
    }

    @Test
    void rainbowStartsRedAndSpreadsTheHues() {
        var base = LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, (byte) 100, false);
        var frame = SoftwareAnimation.frame(base, PRO, 0);
        assertTrue("#ff0000".equalsIgnoreCase(frame.knobConfigs()[0].getColor1()), frame.knobConfigs()[0].getColor1());
        var all = colors(frame);
        assertEquals(all.size(), new HashSet<>(all).size(), "every light has its own colour: " + all);
    }

    @Test
    void rainbowBrightnessScalesTheValue() {
        var base = LightingConfig.createRainbowAnimation((byte) 0, (byte) 0, (byte) 100, false);
        assertEquals("#000000", SoftwareAnimation.frame(base, PRO, 0).knobConfigs()[0].getColor1());
    }

    @Test
    void reverseRainbowMovesTheOtherWay() {
        var speed = (byte) 100;
        var eighth = SoftwareAnimation.periodMs(speed) / 8;
        var forward = SoftwareAnimation.frame(LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, speed, false), PRO, eighth);
        var reverse = SoftwareAnimation.frame(LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, speed, true), PRO, eighth);
        assertEquals(Animations.hsv(1 / 8.0, 1, 1), forward.knobConfigs()[0].getColor1());
        assertEquals(Animations.hsv(7 / 8.0, 1, 1), reverse.knobConfigs()[0].getColor1());
    }

    @Test
    void breathFadesInAndOut() {
        var speed = (byte) 50;
        var base = LightingConfig.createBreathAnimation((byte) 0, (byte) -1, speed);
        assertTrue(colors(SoftwareAnimation.frame(base, PRO, 0)).stream().allMatch("#000000"::equals), "dark at the start");
        var half = SoftwareAnimation.frame(base, PRO, SoftwareAnimation.periodMs(speed) / 2);
        assertTrue(colors(half).stream().allMatch("#FF0000"::equalsIgnoreCase), "full colour halfway: " + colors(half));
    }

    @Test
    void waveIsOneHueWithTheBrightnessTravelling() {
        var speed = (byte) 50;
        var base = LightingConfig.createWaveAnimation((byte) 0, (byte) -1, speed, false, false);
        var at0 = colors(SoftwareAnimation.frame(base, PRO, 0));
        assertTrue(new HashSet<>(at0).size() > 1, "lights differ in brightness: " + at0);
        assertTrue(at0.stream().allMatch(c -> c.substring(3).equals("0000")), "only red, in shades: " + at0);
        var later = colors(SoftwareAnimation.frame(base, PRO, SoftwareAnimation.periodMs(speed) / 4));
        assertNotEquals(at0, later, "the wave moves");
    }

    @Test
    void bouncingWaveTurnsAroundAtTheEnd() {
        var speed = (byte) 50;
        var period = SoftwareAnimation.periodMs(speed);
        var bounce = LightingConfig.createWaveAnimation((byte) 0, (byte) -1, speed, false, true);
        var t = period + period / 4;
        assertEquals(colors(SoftwareAnimation.frame(bounce, PRO, period - period / 4)), colors(SoftwareAnimation.frame(bounce, PRO, t)),
                "a quarter past the end looks like a quarter before it, on the way back");
    }

    @Test
    void customIsShownAsItIs() {
        var base = new LightingConfig(5, 4).toBuilder().lightingMode(LightingMode.CUSTOM).build();
        assertSame(base, SoftwareAnimation.frame(base, PRO, 0));
    }
}
