package com.getpcpanel.integration.device.dto;

import javax.annotation.Nullable;

/**
 * What sets a device's brightness instead of the brightness saved with its lighting, so the UI can grey out
 * its brightness slider and say why. {@code analogIndex} and {@code profile} name the brightness dial: the one
 * setting it for {@link BrightnessSource#DIAL}, the one that takes over once it moves for {@link BrightnessSource#BUTTON}
 * (null when there is none, and the brightness slider takes over instead).
 */
public record BrightnessOwner(BrightnessSource source, @Nullable Integer analogIndex, @Nullable String profile) {
    public static BrightnessOwner button() {
        return new BrightnessOwner(BrightnessSource.BUTTON, null, null);
    }

    public static BrightnessOwner buttonOver(int dialIndex, String profile) {
        return new BrightnessOwner(BrightnessSource.BUTTON, dialIndex, profile);
    }

    public static BrightnessOwner dial(int analogIndex, String profile) {
        return new BrightnessOwner(BrightnessSource.DIAL, analogIndex, profile);
    }
}
