package com.getpcpanel.commands.command;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeProcessMute extends CommandVolume implements ButtonAction {
    private final Set<String> processName;
    private final MuteType muteType;

    public CommandVolumeProcessMute(@JsonProperty("processName") Set<String> processName, @JsonProperty("muteType") MuteType muteType) {
        this.processName = processName;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        getSndCtrl().muteProcesses(processName, muteType);
    }
}
