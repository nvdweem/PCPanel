package com.getpcpanel.device;

/**
 * Fired when the global brightness setting changes.
 */
public record GlobalBrightnessChangedEvent(String serialNum, int brightness) {
}
