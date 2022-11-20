package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeDevice extends CommandVolume implements DialAction {
    private final String deviceId;
    private final boolean unMuteOnVolumeChange;

    @JsonCreator
    public CommandVolumeDevice(@JsonProperty("deviceId") String deviceId, @JsonProperty("isUnMuteOnVolumeChange") boolean unMuteOnVolumeChange) {
        this.deviceId = deviceId;
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
    }

    @Override
    public void execute(DialActionParameters context) {
        if (!context.initial() && unMuteOnVolumeChange) {
            getSndCtrl().muteDevice(deviceId, MuteType.unmute);
        }
        getSndCtrl().setDeviceVolume(deviceId, context.dial() / 100f);
    }
}
