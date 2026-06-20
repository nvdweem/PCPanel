package com.getpcpanel.device.descriptor;

import javax.annotation.Nullable;

/**
 * One analog input (knob/slider/encoder) of a device. The {@code index} is the existing flat
 * integer index the rest of the app keys configuration by (PCPanel: knob i, Pro slider i+5).
 * {@code sourceMin}/{@code sourceMax} describe the raw value range the hardware reports
 * <em>before</em> normalization to the canonical 0-255 analog domain.
 */
public record AnalogInputSpec(
        int index,
        String id,
        String label,
        AnalogKind kind,
        int sourceMin,
        int sourceMax,
        boolean hasButton,
        @Nullable Integer lightOutputIndex
) {
}
