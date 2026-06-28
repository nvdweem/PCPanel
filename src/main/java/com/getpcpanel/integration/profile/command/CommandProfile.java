package com.getpcpanel.integration.profile.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DeviceAction;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@ToString(callSuper = true)
@JsonTypeName("profile.switch")
@CommandMeta(label = "Switch profile", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "refresh", legacyIds = {"com.getpcpanel.commands.command.CommandProfile"})
public class CommandProfile extends Command implements DeviceAction {
    @Getter private final String profile;

    @JsonCreator
    public CommandProfile(@Nullable @JsonProperty("profile") String profile) {
        this.profile = profile;
    }

    @Override
    public void execute(DeviceActionParameters context) {
        CdiHelper.getBean(DeviceHolder.class).getDevice(context.device()).ifPresent(device -> device.switchProfile(profile));
    }

    @Override
    public String buildLabel() {
        return StringUtils.defaultString(profile);
    }
}
