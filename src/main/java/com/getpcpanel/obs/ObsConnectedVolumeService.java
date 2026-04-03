package com.getpcpanel.obs;

import java.util.function.Function;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.WindowsImpl;

@ApplicationScoped
@WindowsImpl
public class ObsConnectedVolumeService extends AbstractNewXVolumeService {
        public void onVoiceMeeterConnected(@Observes OBSConnectEvent event) {
        if (event.connected()) {
            triggerCommandsOf(CommandObsSetSourceVolume.class, Function.identity());
        }
    }
}
