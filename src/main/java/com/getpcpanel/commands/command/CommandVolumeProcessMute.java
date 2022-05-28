package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeProcessMute extends CommandVolume {
    private final String processName;
    private final MuteType muteType;

    public CommandVolumeProcessMute(String device, int knob, String processName, MuteType muteType) {
        super(device, knob);
        this.processName = processName;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        SndCtrl.muteProcess(processName, muteType);
    }
}
