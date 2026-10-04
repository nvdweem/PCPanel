package com.getpcpanel.integration.visualizer.rest;

import javax.annotation.Nullable;

import com.getpcpanel.integration.visualizer.VisualizerService;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.dto.VisualizerSource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Whether the music visualizer can run here, and what it listens to now; its settings travel with the profile's lighting. */
@Path("/api/visualizer")
public class VisualizerResource {
    @Inject VisualizerService visualizer;
    @Inject ISndCtrl sndCtrl;

    @GET
    @Path("/status")
    public VisualizerStatusDto status() {
        var reason = visualizer.unavailableReason();
        return new VisualizerStatusDto(reason == null, reason, name(sndCtrl.defaultPlayer()), name(sndCtrl.defaultRecorder()), visualizer.listeningTo());
    }

    @Nullable
    private String name(@Nullable String deviceId) {
        if (deviceId == null) {
            return null;
        }
        AudioDevice device = sndCtrl.getDevice(deviceId);
        return device == null ? null : device.name();
    }

    /**
     * @param reason        why it isn't available, for the user; null when it is
     * @param defaultOutput the default output's name, when known
     * @param defaultInput  the default input's name, when known
     * @param listening     the source it is capturing right now; null while it isn't
     */
    public record VisualizerStatusDto(boolean available, @Nullable String reason, @Nullable String defaultOutput, @Nullable String defaultInput,
                                      @Nullable VisualizerSource listening) {
    }
}
