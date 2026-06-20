package com.getpcpanel.device.provider;

import com.getpcpanel.device.descriptor.DiscoveryMode;

/**
 * A source of devices. Each provider owns one transport (HID, serial, MIDI) plus the discovery and
 * I/O for it. PCPanel's HID stack is the first provider; future providers (Deej serial, MIDI) plug
 * in as additional {@code @ApplicationScoped} beans discovered via {@code Instance<DeviceProvider>}.
 *
 * <p>On discovery a provider fires a connect event carrying a
 * {@link com.getpcpanel.device.descriptor.DeviceDescriptor}, normalizing its raw analog values to
 * the canonical 0-255 domain at its edge.
 */
public interface DeviceProvider {
    /** Stable provider id, e.g. {@code "pcpanel"}. */
    String id();

    /** Whether this provider enumerates devices automatically or needs manual user setup. */
    DiscoveryMode discoveryMode();

    /** Begin discovery. */
    void start();

    /** Stop discovery and release all resources. */
    void stop();
}
