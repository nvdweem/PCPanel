package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsSetSourceVolume extends CommandObs implements DialAction {
    private final String sourceName;

    @JsonCreator
    public CommandObsSetSourceVolume(@JsonProperty("sourceName") String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public void execute(int volume) {
        OBS.setSourceVolume(sourceName, volume);
    }
}
