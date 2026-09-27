package com.getpcpanel.integration.sonar.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.SonarService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/** Shared target of the Sonar commands: which channel, and which of its mixes (or both). */
@Getter
@ToString(callSuper = true)
public abstract class CommandSonar extends Command {
    private final SonarChannel channel;
    private final SonarMixSelection mix;

    protected CommandSonar(SonarChannel channel, SonarMixSelection mix) {
        // A save missing either field falls back to the editor's defaults; a null route cannot be sent.
        this.channel = channel == null ? SonarChannel.Game : channel;
        this.mix = mix == null ? SonarMixSelection.monitoring : mix;
    }

    protected SonarService getSonarService() {
        return CdiHelper.getBean(SonarService.class);
    }
}
