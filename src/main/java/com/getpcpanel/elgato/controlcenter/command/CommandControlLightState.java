package com.getpcpanel.elgato.controlcenter.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.MuteType;

import dev.niels.elgato.controlcenter.impl.model.ControlCenterLights;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandControlLightState extends CommandControlCenter implements ButtonAction {
    private final MuteType toggleType;

    @JsonCreator
    public CommandControlLightState(
            @JsonProperty("id") @Nullable String id,
            @JsonProperty("toggleType") MuteType toggleType) {
        super(id);
        this.toggleType = toggleType;
    }

    @Override
    public String buildLabel() {
        return "(Un)Mute " + getId();
    }

    @Override
    public void execute() {
        var service = getControlCenterService();
        if (!service.isConnected()) {
            log.warn("Not sending command, controlcenter not connected");
            return;
        }
        if (StringUtils.isBlank(getId())) {
            log.warn("No id specified");
            return;
        }

        var device = service.getDeviceConfig(getId());
        var newDevice = device.withLights(ControlCenterLights.state(toggleType.convert(device.lights().on())));
        service.setDeviceConfiguration(newDevice);
    }
}
