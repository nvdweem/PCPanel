package com.getpcpanel.volume.command;

import com.getpcpanel.commands.command.DialAction;
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
@JsonTypeName("volume.device")
@CommandMeta(label = "Device volume", category = CommandCategory.audio, kinds = {CommandKind.dial}, icon = "volume", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeDevice"})
public class CommandVolumeDevice extends CommandVolume implements DialAction {
    private final String deviceId;
    private final boolean unMuteOnVolumeChange;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandVolumeDevice(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("isUnMuteOnVolumeChange") boolean unMuteOnVolumeChange,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.deviceId = deviceId;
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        if (!context.initial() && unMuteOnVolumeChange) {
            getSndCtrl().muteDevice(deviceId, MuteType.unmute);
        }
        getSndCtrl().setDeviceVolume(deviceId, context.dial().getValue(this, 0, 1));
    }

    @Override
    public String buildLabel() {
        return ("".equals(deviceId) ? "Default" : "Specific") + (unMuteOnVolumeChange ? " (unmute)" : "");
    }
}
