package com.getpcpanel.integration.obs;

import java.util.function.Function;
import com.getpcpanel.integration.obs.command.CommandObsSetSourceVolume;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
@WindowsBuild
public class ObsConnectedVolumeService {
    @Inject DeviceHolder devices;

    public void onVoiceMeeterConnected(@Observes OBSConnectEvent event) {
        if (event.connected()) {
            devices.triggerCommandsOf(CommandObsSetSourceVolume.class, Function.identity());
        }
    }
}
