package com.getpcpanel.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.default-device")
@CommandMeta(label = "Set default device", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "monitor", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeDefaultDevice"})
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

    @Override
    public @Nullable String getOverlayText() {
        var targetDevice = getSndCtrl().getDevicesMap().get(deviceId);
        if (targetDevice != null) {
            return targetDevice.name();
        }
        return null;
    }

    @Override
    public String buildLabel() {
        return "";
    }
}
