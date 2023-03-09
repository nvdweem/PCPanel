package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeFocus extends CommandVolume implements DialAction {
    private final boolean invert;

    @JsonCreator
    public CommandVolumeFocus(@JsonProperty("isInvert") boolean invert) {
        this.invert = invert;
    }

    @Override
    public void execute(DialActionParameters context) {
        getSndCtrl().setFocusVolume(context.dial().calcValue(invert, 0, 1));
    }
}
