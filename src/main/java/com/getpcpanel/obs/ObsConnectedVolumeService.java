package com.getpcpanel.obs;

import java.util.function.Function;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.ConditionalOnWindows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
@ConditionalOnWindows
public class ObsConnectedVolumeService extends AbstractNewXVolumeService {
    protected ObsConnectedVolumeService() {
    }

    @Inject
    public ObsConnectedVolumeService(DeviceHolder devices, Event<Object> eventPublisher) {
        super(devices, eventPublisher);
    }

    public void onVoiceMeeterConnected(@Observes OBSConnectEvent event) {
        if (event.connected()) {
            triggerCommandsOf(CommandObsSetSourceVolume.class, Function.identity());
        }
    }
}
