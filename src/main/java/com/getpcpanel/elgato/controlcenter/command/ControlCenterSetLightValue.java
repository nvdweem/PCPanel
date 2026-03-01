package com.getpcpanel.elgato.controlcenter.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class ControlCenterSetLightValue extends CommandControlCenter implements DialAction {
    @Nullable private final DialCommandParams dialParams;
    @Nullable private final LightValueType type;
    private final boolean minIsOff;
    private final boolean changeIsOn;

    @JsonCreator
    public ControlCenterSetLightValue(
            @JsonProperty("id") @Nullable String id,
            @JsonProperty("type") @Nullable LightValueType type,
            @JsonProperty("minIsOff") boolean minIsOff,
            @JsonProperty("changeIsOn") boolean changeIsOn,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(id);
        this.type = type;
        this.minIsOff = minIsOff;
        this.changeIsOn = changeIsOn;
        this.dialParams = dialParams;
    }

    @Override
    public String buildLabel() {
        return "Set light " + type;
    }

    @Override
    public void execute(DialActionParameters context) {
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

        var value = (double) context.dial().getValue(this, 0, 1);
        var newValue = switch (type) {
            case brightness -> device.withLights(device.lights().withBrightness(value, minIsOff, changeIsOn));
            case temperature -> device.withLights(device.lights().withTemperature(value, minIsOff, changeIsOn));
            case null -> null;
        };
        if (newValue != null) {
            service.setDeviceConfiguration(newValue);
        }
    }

    public enum LightValueType {
        temperature, brightness
    }
}
