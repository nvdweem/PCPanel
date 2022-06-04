package com.getpcpanel.commands.command;

import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsSetScene extends CommandObs implements ButtonAction {
    private final String scene;

    public CommandObsSetScene(String scene) {
        this.scene = scene;
    }

    @Override
    public void execute() {
        OBS.setCurrentScene(scene);
    }
}
