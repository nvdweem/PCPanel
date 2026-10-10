package dev.niels.pulse.model;

import java.util.Map;

import javax.annotation.Nullable;

import dev.niels.pulse.ChannelVolumes;

/**
 * A playback stream (sink input) or recording stream (source output). {@code device} is the sink it plays on or the
 * source it records. A recording stream has a volume only on servers speaking protocol 22 or later; it is {@code null}
 * otherwise.
 */
public record StreamInfo(
        int index,
        @Nullable String name,
        int client,
        int device,
        @Nullable ChannelVolumes volume,
        boolean muted,
        boolean corked,
        @Nullable String driver,
        Map<String, String> properties) {
}
