package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeDeviceMute extends CommandVolume implements ButtonAction {
    private final String deviceId;
    private final MuteType muteType;

    @JsonCreator
    public CommandVolumeDeviceMute(@JsonProperty("deviceId") String deviceId, @JsonProperty("muteType") MuteType muteType) {
        this.deviceId = deviceId;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        getSndCtrl().muteDevice(deviceId, muteType);
    }

    @Override
    public String buildLabel() {
        return String.valueOf(muteType);
    }
}
