package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;

class ProVisualColorsServiceTest {
    @Test
    void rainbowLogoUsesConfiguredHueAndBrightness() throws Exception {
        var service = new ProVisualColorsService();
        var logo = new SingleLogoLightingConfig();
        logo.setMode(SINGLE_LOGO_MODE.RAINBOW);
        logo.setHue((byte) 85);
        logo.setBrightness((byte) 255);

        var color = service.resolveLogoColor(logo);

        assertEquals("$RAINBOW!", color);
    }

    @Test
    void volumeGradientDialEmitsTokenWithBothColors() {
        var service = new ProVisualColorsService();
        var knob = new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.VOLUME_GRADIENT)
                                                 .setColor1("#FF0000")
                                                 .setColor2("#0000FF");

        // The UI interpolates this token per live value (color1 at 0, color2 at 100).
        assertEquals("$VOLGRAD!#FF0000!#0000FF", service.resolveDialColor(knob));
    }

    @Test
    void staticDialUsesItsColor() {
        var service = new ProVisualColorsService();
        var knob = new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#FF0000");

        assertEquals("#FF0000", service.resolveDialColor(knob));
    }
}
