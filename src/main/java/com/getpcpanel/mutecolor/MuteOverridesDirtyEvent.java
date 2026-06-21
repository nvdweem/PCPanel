package com.getpcpanel.mutecolor;

/**
 * Fired when a resolver's internally-cached mute state has changed (e.g. a VoiceMeeter mute toggle)
 * and the mute-override colours must be recomputed. {@link MuteColorService} observes it and re-applies
 * overrides. Resolvers fire this AFTER updating their cache, so the recompute always sees fresh state.
 */
public record MuteOverridesDirtyEvent() {
}
