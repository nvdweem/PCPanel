package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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

        assertEquals("#00ff00", color);
    }
}
