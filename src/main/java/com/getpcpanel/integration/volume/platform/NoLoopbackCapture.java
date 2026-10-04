package com.getpcpanel.integration.volume.platform;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Where capture isn't implemented (macOS): the visualizer is unavailable. */
@DefaultBean
@ApplicationScoped
class NoLoopbackCapture implements LoopbackCapture {
    @Override
    public boolean supported() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return "The music visualizer isn't available on this platform yet.";
    }

    @Override
    public boolean start(String deviceId, boolean input) {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public int sampleRate() {
        return 22_050;
    }

    @Override
    public int read(float[] into) {
        return 0;
    }
}
