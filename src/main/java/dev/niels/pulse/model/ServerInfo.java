package dev.niels.pulse.model;

import javax.annotation.Nullable;

/** What {@code pactl info} shows: who runs the server and which sink and source are the defaults. */
public record ServerInfo(
        @Nullable String userName,
        @Nullable String hostName,
        @Nullable String serverVersion,
        @Nullable String serverName,
        @Nullable String defaultSinkName,
        @Nullable String defaultSourceName) {
}
