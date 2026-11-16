package com.getpcpanel.integration.device.dto;

import javax.annotation.Nullable;

/**
 * What sets a device's brightness instead of the brightness saved with its lighting, so the UI can grey out
 * its brightness slider and say why. {@code analogIndex} and {@code profile} name the control for {@link BrightnessSource#DIAL}.
 */
public record BrightnessOwner(BrightnessSource source, @Nullable Integer analogIndex, @Nullable String profile) {
    public static BrightnessOwner button() {
        return new BrightnessOwner(BrightnessSource.BUTTON, null, null);
    }

    public static BrightnessOwner dial(int analogIndex, String profile) {
        return new BrightnessOwner(BrightnessSource.DIAL, analogIndex, profile);
    }
}
