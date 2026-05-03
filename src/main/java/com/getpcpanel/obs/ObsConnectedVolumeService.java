package com.getpcpanel.obs;

import java.util.function.Function;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
@WindowsBuild
public class ObsConnectedVolumeService extends AbstractNewXVolumeService {
        public void onVoiceMeeterConnected(@Observes OBSConnectEvent event) {
        if (event.connected()) {
            triggerCommandsOf(CommandObsSetSourceVolume.class, Function.identity());
        }
    }
}
