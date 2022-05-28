package com.getpcpanel.commands.command;

import com.getpcpanel.obs.OBS;

import lombok.Getter;

@Getter
public class CommandObsSetSource extends CommandObs {
    private final String sourceName;
    private final int volume;

    public CommandObsSetSource(String device, int knob, String sourceName, int volume) {
        super(device, knob);
        this.sourceName = sourceName;
        this.volume = volume;
    }

    @Override
    public void execute() {
        OBS.setSourceVolume(sourceName, volume);
    }
}
