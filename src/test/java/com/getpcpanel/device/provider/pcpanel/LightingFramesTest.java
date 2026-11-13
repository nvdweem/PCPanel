package com.getpcpanel.device.provider.pcpanel;

import static com.getpcpanel.device.provider.pcpanel.LightingFrames.Darkness.DARK;
import static com.getpcpanel.device.provider.pcpanel.LightingFrames.Darkness.LIT;
import static com.getpcpanel.device.provider.pcpanel.LightingFrames.Darkness.UNKNOWN;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.PREFIX_MINI;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.PREFIX_PRO;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.STATIC_COLOR_MINI;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.STATIC_COLOR_PRO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

/** The trace's dark/lit verdict, read from the bytes the production encoders write. */
class LightingFramesTest {
    private static final String RED = "#ff0000";
    private static final String BLACK = "#000000";
    private static final int FULL = 100;
    private static final int KNOBS = 5;
    private static final int SLIDERS = 4;
    /** A whole-panel animation at full brightness (0xFF as a signed byte). */
    private static final byte BRIGHT = (byte) 0xFF;

    @Test
    void wholePanelColour() {
        assertEquals(LIT, LightingFrames.classify(OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, LightingConfig.createAllColor(RED))));
        assertEquals(LIT, LightingFrames.classify(OutputInterpreter.allColorReport(PREFIX_MINI, STATIC_COLOR_MINI, LightingConfig.createAllColor(RED))));
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, LightingConfig.createAllColor(BLACK))),
                "the sleep detector's lights-off is a whole-panel black");
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allColorReport(PREFIX_MINI, STATIC_COLOR_MINI, LightingConfig.createAllColor(BLACK))));
    }

    @Test
    void zeroBrightnessIsDarkWhateverTheColour() {
        var red = LightingConfig.createAllColor(RED);
        red.setGlobalBrightness(0);
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, red)));

        var wave = LightingConfig.createWaveAnimation((byte) 0, BRIGHT, (byte) 0, false, false);
        wave.setGlobalBrightness(0);
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allWaveReport(PREFIX_PRO, wave)));
    }

    @Test
    void wholePanelAnimations() {
        assertEquals(LIT, LightingFrames.classify(OutputInterpreter.allWaveReport(PREFIX_PRO, LightingConfig.createWaveAnimation((byte) 0, BRIGHT, (byte) 0, false, false))));
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allWaveReport(PREFIX_PRO, LightingConfig.createWaveAnimation((byte) 0, (byte) 0, (byte) 0, false, false))));
        assertEquals(LIT, LightingFrames.classify(OutputInterpreter.allBreathReport(PREFIX_MINI, LightingConfig.createBreathAnimation((byte) 0, BRIGHT, (byte) 0))));
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allBreathReport(PREFIX_MINI, LightingConfig.createBreathAnimation((byte) 0, (byte) 0, (byte) 0))));
        assertEquals(LIT, LightingFrames.classify(OutputInterpreter.allRainbowReport(PREFIX_PRO, LightingConfig.createRainbowAnimation((byte) 0, BRIGHT, (byte) 0, false))));
        assertEquals(DARK, LightingFrames.classify(OutputInterpreter.allRainbowReport(PREFIX_PRO, LightingConfig.createRainbowAnimation((byte) 0, (byte) 0, (byte) 0, false))));
    }

    /** A Pro vertical rainbow starts like a Pro whole-panel colour; it must never read as dark. */
    @Test
    void proVerticalRainbowIsNeverTakenForDark() {
        var rainbow = LightingConfig.createRainbowAnimation((byte) 0, (byte) 0, (byte) 0, false, true);
        assertNotEquals(DARK, LightingFrames.classify(OutputInterpreter.allRainbowReport(PREFIX_PRO, rainbow)));
    }

    @Test
    void perControlLightingIsDarkOnlyWhenEveryLightIs() {
        var knobs = knobs(SINGLE_KNOB_MODE.NONE, BLACK);
        var sliders = sliders(SINGLE_SLIDER_MODE.NONE, BLACK);
        var logo = new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.NONE);
        assertEquals(DARK, customFrame(knobs, sliders, logo), "every light off");

        assertEquals(DARK, customFrame(knobs(SINGLE_KNOB_MODE.STATIC, BLACK), sliders(SINGLE_SLIDER_MODE.STATIC, BLACK),
                new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(BLACK)), "the dark frame: every light black");

        var oneKnob = knobs(SINGLE_KNOB_MODE.NONE, BLACK);
        oneKnob[2] = new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(RED);
        assertEquals(LIT, customFrame(oneKnob, sliders, logo), "one knob lit");

        var oneSlider = sliders(SINGLE_SLIDER_MODE.NONE, BLACK);
        oneSlider[SLIDERS - 1] = new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(RED);
        assertEquals(LIT, customFrame(knobs, oneSlider, logo), "the last slider lit");

        assertEquals(LIT, customFrame(knobs, sliders, new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(RED)), "the logo lit");
    }

    @Test
    void reportsItCannotReadAreUnknown() {
        assertEquals(UNKNOWN, LightingFrames.classify(new byte[] { 1 }), "the init message");
        assertEquals(UNKNOWN, LightingFrames.classify(), "nothing at all");
        assertEquals(UNKNOWN, LightingFrames.classify(OutputInterpreter.buildFullLEDData(FULL, new String[] { RED }, null, null)), "the RGB model");
    }

    @Test
    void fingerprintAndHexDescribeTheBytes() {
        var black = OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, LightingConfig.createAllColor(BLACK));
        var red = OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, LightingConfig.createAllColor(RED));
        assertEquals(LightingFrames.fingerprint(black), LightingFrames.fingerprint(black.clone()));
        assertNotEquals(LightingFrames.fingerprint(black), LightingFrames.fingerprint(red));
        assertEquals("050402ff", LightingFrames.hex(red), "trailing padding is dropped");
        assertEquals("050402|050402ff", LightingFrames.hex(black, red));
    }

    private static LightingFrames.Darkness customFrame(SingleKnobLightingConfig[] knobs, SingleSliderLightingConfig[] sliders, SingleLogoLightingConfig logo) {
        return LightingFrames.classify(
                OutputInterpreter.buildKnobData(PREFIX_PRO, FULL, knobs),
                OutputInterpreter.buildSliderData(FULL, sliders),
                OutputInterpreter.buildLogoData(FULL, logo));
    }

    private static SingleKnobLightingConfig[] knobs(SINGLE_KNOB_MODE mode, String color) {
        var knobs = new SingleKnobLightingConfig[KNOBS];
        for (var i = 0; i < knobs.length; i++) {
            knobs[i] = new SingleKnobLightingConfig().setMode(mode).setColor1(color);
        }
        return knobs;
    }

    private static SingleSliderLightingConfig[] sliders(SINGLE_SLIDER_MODE mode, String color) {
        var sliders = new SingleSliderLightingConfig[SLIDERS];
        for (var i = 0; i < sliders.length; i++) {
            sliders[i] = new SingleSliderLightingConfig().setMode(mode).setColor1(color);
        }
        return sliders;
    }
}
