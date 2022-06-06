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

    @JsonCreator
    public CommandObsSetSourceVolume(@JsonProperty("sourceName") String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public void execute(boolean initial, int volume) {
        var obs = MainFX.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.setSourceVolume(sourceName, volume);
        }
    }
}
