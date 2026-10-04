package com.getpcpanel.integration.volume.platform;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Where playback can't be seen: nothing plays. */
@DefaultBean
@ApplicationScoped
class NoPlaybackGate implements PlaybackGate {
    @Override
    public Playing check() {
        return Playing.NOTHING;
    }
}
