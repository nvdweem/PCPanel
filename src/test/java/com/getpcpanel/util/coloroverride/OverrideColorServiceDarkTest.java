package com.getpcpanel.util.coloroverride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.sleepdetection.SleepDetector;

/** While the panels show dark frames only the overrides of features that show on dark panels count. */
class OverrideColorServiceDarkTest {
    private static final String SERIAL = "pro";
    private OverrideColorService sut;
    private SleepDetector sleep;
    private Provider darkShowing;
    private Provider other;

    /** A colour override provider that may or may not show on dark panels. */
    static final class Provider extends ColorOverrideHolder {
        boolean dark;

        @Override
        public boolean showsWhileDark() {
            return dark;
        }
    }

    private static SingleKnobLightingConfig knob(String color) {
        return new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color);
    }

    @BeforeEach
    void setUp() {
        darkShowing = new Provider();
        darkShowing.dark = true;
        other = new Provider();
        sleep = mock(SleepDetector.class);
        sut = new OverrideColorService();
        sut.setOverriders(List.of(other, darkShowing)); // the one not shown while dark has priority
        sut.sleep = sleep;
    }

    @Test
    void whileLitEveryProviderCountsInPriorityOrder() {
        other.setDialOverride(SERIAL, 0, knob("#111111"));
        darkShowing.setDialOverride(SERIAL, 0, knob("#222222"));
        assertEquals("#111111", sut.getDialOverride(SERIAL, 0).map(SingleKnobLightingConfig::getColor1).orElseThrow());
    }

    @Test
    void whileShowingDarkFramesOnlyProvidersThatShowOnDarkPanelsCount() {
        when(sleep.showsDarkFrames()).thenReturn(true);
        other.setDialOverride(SERIAL, 0, knob("#111111"));
        other.setDialOverride(SERIAL, 1, knob("#111111"));
        darkShowing.setDialOverride(SERIAL, 0, knob("#222222"));
        assertEquals("#222222", sut.getDialOverride(SERIAL, 0).map(SingleKnobLightingConfig::getColor1).orElseThrow());
        assertEquals(Optional.empty(), sut.getDialOverride(SERIAL, 1));
    }

    @Test
    void anyOverrideLooksAtEveryLightOfTheLayout() {
        assertFalse(sut.anyOverride(SERIAL, new Layout(5, 4)));
        darkShowing.setLogoOverride(SERIAL, new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor("#ff0000"));
        assertTrue(sut.anyOverride(SERIAL, new Layout(5, 4)));
        darkShowing.setLogoOverride(SERIAL, null);
        darkShowing.setDialOverride(SERIAL, 4, knob("#ff0000"));
        assertTrue(sut.anyOverride(SERIAL, new Layout(5, 4)));
        assertFalse(sut.anyOverride(SERIAL, new Layout(4, 0)), "a light the device doesn't have");
    }

    @Test
    void anyOverrideWhileShowingDarkFramesIgnoresTheOthers() {
        when(sleep.showsDarkFrames()).thenReturn(true);
        other.setDialOverride(SERIAL, 0, knob("#111111"));
        assertFalse(sut.anyOverride(SERIAL, new Layout(5, 4)));
    }
}
