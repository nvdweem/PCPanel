package com.getpcpanel.device.descriptor;

/**
 * How a {@code DeviceProvider} surfaces devices: automatically enumerated (HID, MIDI) or
 * manually added by the user (a serial port + baud rate).
 */
public enum DiscoveryMode {
    AUTO, MANUAL
}
