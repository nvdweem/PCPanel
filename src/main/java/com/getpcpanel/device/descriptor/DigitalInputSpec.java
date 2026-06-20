package com.getpcpanel.device.descriptor;

/**
 * A standalone digital (button) input. PCPanel buttons share an analog index (see
 * {@link AnalogInputSpec#hasButton()}); {@code standalone} buttons that occupy their own index
 * are described here.
 */
public record DigitalInputSpec(
        int index,
        String id,
        String label,
        boolean standalone
) {
}
