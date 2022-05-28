package com.getpcpanel.commands.command;

import com.getpcpanel.obs.OBS;

import lombok.Getter;

@Getter
public class CommandObsSetScene extends CommandObs {
    private final String scene;

    public CommandObsSetScene(String device, int knob, String scene) {
        super(device, knob);
        this.scene = scene;
    }

    @Override
    public void execute() {
        OBS.setCurrentScene(scene);
    }
}
