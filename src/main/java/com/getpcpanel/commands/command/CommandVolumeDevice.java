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
    private final boolean invert;

    @JsonCreator
    public CommandVolumeDevice(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("isUnMuteOnVolumeChange") boolean unMuteOnVolumeChange,
            @JsonProperty("isInvert") boolean invert) {
        this.deviceId = deviceId;
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
        this.invert = invert;
    }

    @Override
    public void execute(DialActionParameters context) {
        if (!context.initial() && unMuteOnVolumeChange) {
            getSndCtrl().muteDevice(deviceId, MuteType.unmute);
        }
        getSndCtrl().setDeviceVolume(deviceId, context.dial().calcValue(invert, 0, 1));
    }

    @Override
    public String buildLabel() {
        return ("".equals(deviceId) ? "Default" : "Specific") + (unMuteOnVolumeChange ? " (unmute)" : "");
    }
}
