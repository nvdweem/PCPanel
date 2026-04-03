package com.getpcpanel.voicemeeter;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.spring.WindowsImpl;

@ApplicationScoped
@WindowsImpl
public class VoiceMeeterConnectedVolumeService extends AbstractNewXVolumeService {
        public void onVoiceMeeterConnected() {
        triggerCommandsOf(CommandVoiceMeeter.class,
                s -> s.filterValues(cmd -> cmd instanceof CommandVoiceMeeterBasic || cmd instanceof CommandVoiceMeeterAdvanced)
        );
    }
}
