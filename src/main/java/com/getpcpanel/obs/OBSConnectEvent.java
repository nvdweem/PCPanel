package com.getpcpanel.obs;

/**
 * Represents an event indicating a change in the OBS connection status.
 * This is an immutable record with a single field {@code connected}.
 *
 * @param connected {@code true} if connected to OBS, {@code false} otherwise.
 */
public record OBSConnectEvent(boolean connected) {
}
