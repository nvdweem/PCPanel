package com.getpcpanel.device.descriptor;

import java.util.List;

/**
 * Device-wide lighting capabilities (the PCPanel firmware-animated modes + global brightness).
 * Null on devices without any global lighting.
 */
public record GlobalLightingSpec(
        List<String> supportedModes,
        boolean hasGlobalBrightness,
        int brightnessMin,
        int brightnessMax,
        boolean firmwareAnimated
) {
}
