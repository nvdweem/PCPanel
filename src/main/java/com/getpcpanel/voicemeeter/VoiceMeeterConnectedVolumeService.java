package com.getpcpanel.voicemeeter;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
@WindowsBuild
public class VoiceMeeterConnectedVolumeService extends AbstractNewXVolumeService {
        public void onVoiceMeeterConnected(@Observes VoiceMeeterConnectedEvent event) {
        triggerCommandsOf(CommandVoiceMeeter.class,
                s -> s.filterValues(cmd -> cmd instanceof CommandVoiceMeeterBasic || cmd instanceof CommandVoiceMeeterAdvanced)
        );
    }
}
