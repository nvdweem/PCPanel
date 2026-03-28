package com.getpcpanel.voicemeeter;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.ConditionalOnWindows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
@ConditionalOnWindows
public class VoiceMeeterConnectedVolumeService extends AbstractNewXVolumeService {
    public VoiceMeeterConnectedVolumeService(DeviceHolder devices, Event<Object> eventPublisher) {
        super(devices, eventPublisher);
    }

    public void onVoiceMeeterConnected(@Observes VoiceMeeterConnectedEvent event) {
        triggerCommandsOf(CommandVoiceMeeter.class,
                s -> s.filterValues(cmd -> cmd instanceof CommandVoiceMeeterBasic || cmd instanceof CommandVoiceMeeterAdvanced)
        );
    }
}
