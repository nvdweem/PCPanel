package com.getpcpanel.integration.voicemeeter;

import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeter;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterBasic;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
@WindowsBuild
public class VoiceMeeterConnectedVolumeService {
    @Inject DeviceHolder devices;

    public void onVoiceMeeterConnected(@Observes VoiceMeeterConnectedEvent event) {
        devices.triggerCommandsOf(CommandVoiceMeeter.class, s -> s.filterValues(cmd -> cmd instanceof CommandVoiceMeeterBasic || cmd instanceof CommandVoiceMeeterAdvanced));
    }
}
