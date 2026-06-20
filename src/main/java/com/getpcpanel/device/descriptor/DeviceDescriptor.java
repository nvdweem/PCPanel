package com.getpcpanel.device.descriptor;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The capability descriptor for a connected device: everything the rest of the app needs to know
 * about a device (how many analog/digital inputs it has, their ranges, what lights/outputs it
 * carries) as data supplied by a provider rather than a {@code switch} on a hardware-model enum.
 *
 * <p>This is carried on the connect event and surfaced to the frontend via the device DTOs.
 */
public record DeviceDescriptor(
        String providerId,
        String deviceKindId,
        String displayName,
        List<AnalogInputSpec> analogInputs,
        List<DigitalInputSpec> digitalInputs,
        List<LightOutputSpec> lightOutputs,
        List<AnalogOutputSpec> analogOutputs,
        @Nullable GlobalLightingSpec globalLighting
) {
}
