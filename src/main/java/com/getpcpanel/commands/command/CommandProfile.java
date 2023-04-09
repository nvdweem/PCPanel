package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.hid.DeviceHolder;

import javafx.application.Platform;
import lombok.Getter;
import lombok.ToString;

@ToString(callSuper = true)
public class CommandProfile extends Command implements DeviceAction {
    @Getter private final String profile;

    @JsonCreator
    public CommandProfile(@Nullable @JsonProperty("profile") String profile) {
        this.profile = profile;
    }

    @Override
    public void execute(DeviceActionParameters context) {
        Platform.runLater(() -> MainFX.getBean(DeviceHolder.class).getDevice(context.device()).ifPresent(device -> device.setProfile(profile)));
    }

    @Override
    public String buildLabel() {
        return StringUtils.defaultString(profile);
    }
}
