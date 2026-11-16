package com.getpcpanel.integration.device.dto;

/** What sets a device's brightness instead of the brightness saved with its lighting. */
public enum BrightnessSource {
    /** A dial or slider carrying the Brightness action, in any of the device's profiles. */
    DIAL,
    /** A button's "Run dial actions at a level", until the brightness dial moves again. */
    BUTTON
}
