package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.SaveService;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.jbosslog.JBossLog;

@Getter
@JBossLog
@ToString(callSuper = true)
public class CommandBrightness extends Command implements DialAction {
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandBrightness(@JsonProperty("dialParams") DialCommandParams dialParams) {
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        MainFX.getBean(DeviceHolder.class).getDevice(context.device()).ifPresent(device -> {
            var lightingConfig = device.getLightingConfig();
            lightingConfig.setGlobalBrightness(context.dial().getValue(this));
            device.setLighting(lightingConfig, false);

            MainFX.getBean(SaveService.class).debouncedSave();
        });
    }

    @Override
    public String buildLabel() {
        return "";
    }
}
