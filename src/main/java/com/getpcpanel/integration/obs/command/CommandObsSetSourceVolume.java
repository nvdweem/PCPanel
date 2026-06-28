package com.getpcpanel.integration.obs.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("obs.set-source-volume")
@CommandMeta(label = "OBS — source volume", category = CommandCategory.integration, kinds = {CommandKind.dial}, integration = "obs", icon = "sliders", legacyIds = {"com.getpcpanel.commands.command.CommandObsSetSourceVolume"})
public class CommandObsSetSourceVolume extends CommandObs implements DialAction {
    private final String sourceName;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandObsSetSourceVolume(
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.sourceName = sourceName;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        var obs = CdiHelper.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.setSourceVolume(sourceName, context.dial().getValue(this));
        }
    }

    @Override
    public String buildLabel() {
        return "Source volume: " + sourceName;
    }
}
