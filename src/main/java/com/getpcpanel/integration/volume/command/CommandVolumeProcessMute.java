package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.process-mute")
@CommandMeta(label = "App mute", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "volume-x", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeProcessMute"})
public class CommandVolumeProcessMute extends CommandVolume implements ButtonAction {
    private final Set<String> processName;
    private final MuteType muteType;

    public CommandVolumeProcessMute(@JsonProperty("processName") Set<String> processName, @JsonProperty("muteType") MuteType muteType) {
        this.processName = processName;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        getSndCtrl().muteProcesses(CdiHelper.getBean(EverythingElse.class).expand(processName), muteType);
    }

    @Override
    public String buildLabel() {
        return muteType + " - " + processName;
    }
}
