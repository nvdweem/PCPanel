package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.hid.DeviceHolder;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandBrightness extends Command implements DialAction {
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandBrightness(@JsonProperty("dialParams") DialCommandParams dialParams) {
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        // Global brightness is now a runtime override resolved from this control's live position in the
        // lighting output path (BrightnessService): it wins over the saved per-profile value and survives
        // profile switches. Turning the dial just re-applies the current lighting so the new brightness
        // shows; nothing is persisted.
        CdiHelper.getBean(DeviceHolder.class).getDevice(context.device())
                 .ifPresent(device -> device.setLighting(device.lightingConfig(), false));
    }

    @Override
    public String buildLabel() {
        return "";
    }
}
