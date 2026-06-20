package com.getpcpanel.device.descriptor;

/**
 * An analog output (e.g. a MIDI LED ring driven by a 0-254 value). Net-new concept; no PCPanel
 * device exposes one today.
 */
public record AnalogOutputSpec(
        int index,
        String id,
        String label,
        int min,
        int max
) {
}
