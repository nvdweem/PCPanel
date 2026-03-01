package dev.niels.elgato.controlcenter.impl.model;

import lombok.With;

@With
public record ControlCenterDeviceConfiguration(String deviceID,
                                               ControlCenterLights lights) {
}
