package com.getpcpanel.commands.command;

import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsSetSourceVolume extends CommandObs implements DialAction {
    private final String sourceName;

    public CommandObsSetSourceVolume(String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public void execute(int volume) {
        OBS.setSourceVolume(sourceName, volume);
    }
}
