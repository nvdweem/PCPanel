package com.getpcpanel.integration.volume;

import java.util.function.Function;

import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.volume.command.CommandVolume;
import com.getpcpanel.integration.volume.platform.AudioReadyEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Applies the volume dials' levels again once the audio backend is ready. A device sends its positions as soon as it
 * connects, which at startup can be before the backend has listed the apps and devices those levels are for.
 */
@ApplicationScoped
class AudioReadyVolumeService {
    @Inject DeviceHolder devices;

    public void onAudioReady(@Observes AudioReadyEvent event) {
        devices.triggerCommandsOf(CommandVolume.class, Function.identity(), true);
    }
}
