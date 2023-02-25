package com.getpcpanel.commands;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.voicemeeter.VoiceMeeterConnectedEvent;

@Service
@ConditionalOnWindows
public class VoiceMeeterConnectedVolumeService extends AbstractNewXVolumeService {
    public VoiceMeeterConnectedVolumeService(DeviceHolder devices, ApplicationEventPublisher eventPublisher) {
        super(devices, eventPublisher);
    }

    @EventListener(VoiceMeeterConnectedEvent.class)
    public void onVoiceMeeterConnected() {
        triggerCommandsOf(CommandVoiceMeeter.class,
                s -> s.filterValues(cmd -> cmd instanceof CommandVoiceMeeterBasic || cmd instanceof CommandVoiceMeeterAdvanced)
        );
    }
}
