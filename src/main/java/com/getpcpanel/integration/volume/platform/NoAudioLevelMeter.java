package com.getpcpanel.integration.volume.platform;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** The meter where none is implemented: audio-level lights show their loud colour. */
@DefaultBean
@ApplicationScoped
class NoAudioLevelMeter implements AudioLevelMeter {
    @Override
    public boolean supported() {
        return false;
    }

    @Override
    public Levels sample() {
        return Levels.NONE;
    }
}
