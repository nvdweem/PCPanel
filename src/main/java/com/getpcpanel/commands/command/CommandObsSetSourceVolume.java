package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsSetSourceVolume extends CommandObs implements DialAction {
    private final String sourceName;
    private final boolean invert;

    @JsonCreator
    public CommandObsSetSourceVolume(
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("isInvert") boolean invert) {
        this.sourceName = sourceName;
        this.invert = invert;
    }

    @Override
    public void execute(DialActionParameters context) {
        var obs = MainFX.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.setSourceVolume(sourceName, context.dial().calcValue(invert));
        }
    }
}
