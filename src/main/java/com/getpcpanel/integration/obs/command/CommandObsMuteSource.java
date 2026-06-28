package com.getpcpanel.integration.obs.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.integration.obs.OBS;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("obs.mute-source")
@CommandMeta(label = "OBS — mute source", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "obs", icon = "mic-off", legacyIds = {"com.getpcpanel.commands.command.CommandObsMuteSource"})
public class CommandObsMuteSource extends CommandObs implements ButtonAction {
    private final String source;
    private final MuteType type;

    @JsonCreator
    public CommandObsMuteSource(@JsonProperty("source") String source, @JsonProperty("type") MuteType type) {
        this.source = source;
        this.type = type;
    }

    @Override
    public void execute() {
        var obs = CdiHelper.getBean(OBS.class);
        if (obs.isConnected()) {
            switch (type) {
                case toggle -> obs.toggleSourceMute(source);
                case mute -> obs.setSourceMute(source, true);
                case unmute -> obs.setSourceMute(source, false);
            }
        }
    }

    @Override
    public String buildLabel() {
        return "Mute source: " + source + " (" + type + ")";
    }
}
