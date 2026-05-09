package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.volume.VolumeCoordinatorService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeFocus extends CommandVolume implements DialAction {
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandVolumeFocus(@JsonProperty("dialParams") DialCommandParams dialParams) {
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        CdiHelper.getBean(VolumeCoordinatorService.class).setFocusVolume(context.dial().getValue(this, 0, 1));
    }

    @Override
    public String buildLabel() {
        return "";
    }
}

