package com.getpcpanel.integration.obs.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("obs.set-scene")
@CommandMeta(label = "OBS — switch scene", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "obs", icon = "film", legacyIds = {"com.getpcpanel.commands.command.CommandObsSetScene"})
public class CommandObsSetScene extends CommandObs implements ButtonAction {
    private final String scene;

    @JsonCreator
    public CommandObsSetScene(@JsonProperty("scene") String scene) {
        this.scene = scene;
    }

    @Override
    public void execute() {
        var obs = CdiHelper.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.setCurrentScene(scene);
        }
    }

    @Override
    public String buildLabel() {
        return "Set scene: " + scene;
    }
}
