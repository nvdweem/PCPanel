package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsSetScene extends CommandObs implements ButtonAction {
    private final String scene;

    @JsonCreator
    public CommandObsSetScene(@JsonProperty("scene") String scene) {
        this.scene = scene;
    }

    @Override
    public void execute() {
        var obs = MainFX.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.setCurrentScene(scene);
        }
    }

    @Override
    public String buildLabel() {
        return "Set scene: " + scene;
    }
}
