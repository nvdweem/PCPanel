package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.volume.platform.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.focus-mute")
@CommandMeta(label = "Focused-app mute", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "volume-x", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeFocusMute"})
public class CommandVolumeFocusMute extends CommandVolume implements ButtonAction {
    private final MuteType muteType;

    public CommandVolumeFocusMute(@JsonProperty("muteType") MuteType muteType) {
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        getSndCtrl().muteProcesses(Set.of(getSndCtrl().getFocusApplication()), muteType);
    }

    @Override
    public String buildLabel() {
        return String.valueOf(muteType);
    }
}
