package dev.niels.pulse.model;

import java.util.Map;

import javax.annotation.Nullable;

import dev.niels.pulse.ChannelVolumes;

/**
 * A sink (output) or source (input). {@code monitor} is the other half of a monitor pair: for a sink the source that
 * records it, for a source the sink it monitors ({@code -1} when there is none).
 */
public record DeviceInfo(
        int index,
        String name,
        @Nullable String description,
        ChannelVolumes volume,
        boolean muted,
        int monitor,
        @Nullable String monitorName,
        @Nullable String driver,
        Map<String, String> properties,
        @Nullable String activePort) {
}
