package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeProcessMute extends CommandVolume implements ButtonAction {
    private final String processName;
    private final MuteType muteType;

    @JsonCreator
    public CommandVolumeProcessMute(@JsonProperty("processName") String processName, @JsonProperty("muteType") MuteType muteType) {
        this.processName = processName;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        SndCtrl.muteProcess(processName, muteType);
    }
}
