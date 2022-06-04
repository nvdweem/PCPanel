package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        if (OBS.isConnected()) {
            OBS.setCurrentScene(scene);
        }
    }
}
