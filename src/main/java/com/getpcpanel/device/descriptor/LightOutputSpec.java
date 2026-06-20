package com.getpcpanel.device.descriptor;

import java.util.List;

/**
 * One addressable light output of a device.
 */
public record LightOutputSpec(
        int index,
        String id,
        String label,
        LightColorModel colorModel,
        LightGroupKind group,
        List<String> supportedElementModes
) {
}
