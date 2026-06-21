package com.getpcpanel.wavelink;

/**
 * Fired whenever Wave Link reports a state change (a channel/mix/output changed, incl. its mute state).
 * It carries no payload — observers re-read the live state they care about. Used by the mute-colour
 * layer to re-evaluate overrides for Wave Link-controlled dials.
 */
public record WaveLinkChangedEvent() {
}
