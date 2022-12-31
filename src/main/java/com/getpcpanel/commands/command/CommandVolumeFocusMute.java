package com.getpcpanel.commands.command;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeFocusMute extends CommandVolume implements ButtonAction {
    private final MuteType muteType;

    public CommandVolumeFocusMute(@JsonProperty("muteType") MuteType muteType) {
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        getSndCtrl().muteProcesses(Set.of(getSndCtrl().getFocusApplication()), muteType);
    }
}
