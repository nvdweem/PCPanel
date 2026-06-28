package com.getpcpanel.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.device-mute")
@CommandMeta(label = "Device mute", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "volume-x", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeDeviceMute"})
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
