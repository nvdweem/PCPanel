package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeDefaultDevice extends CommandVolume implements ButtonAction {
    private final String deviceId;

    @JsonCreator
    public CommandVolumeDefaultDevice(@JsonProperty("deviceId") String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void execute() {
        getSndCtrl().setDefaultDevice(deviceId);
    }
}
