package com.getpcpanel.obs;

import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.ConditionalOnWindows;

@Service
@ConditionalOnWindows
public class ObsConnectedVolumeService extends AbstractNewXVolumeService {
    public ObsConnectedVolumeService(DeviceHolder devices, ApplicationEventPublisher eventPublisher) {
        super(devices, eventPublisher);
    }

    @EventListener
    public void onVoiceMeeterConnected(OBSConnectEvent event) {
        if (event.connected()) {
            triggerCommandsOf(CommandObsSetSourceVolume.class, Function.identity());
        }
    }
}
