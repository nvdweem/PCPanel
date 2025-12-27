package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.sonar.SonarService;
import com.getpcpanel.sonar.SonarService.SonarChannel;
import com.getpcpanel.sonar.SonarService.SonarMode;
import com.getpcpanel.sonar.SonarService.SonarTarget;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandSteelSeriesSonarVolume extends Command implements DialAction {
    private final SonarMode mode;
    private final SonarTarget target;
    private final SonarChannel channel;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandSteelSeriesSonarVolume(
            @JsonProperty("mode") SonarMode mode,
            @JsonProperty("target") SonarTarget target,
            @JsonProperty("channel") SonarChannel channel,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.mode = mode == null ? SonarMode.AUTO : mode;
        this.target = target == null ? SonarTarget.MONITORING : target;
        this.channel = channel == null ? SonarChannel.MEDIA : channel;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        var sonar = MainFX.getBean(SonarService.class);
        sonar.setVolume(mode, target, channel, context.dial().getValue(this, 0, 1));
    }

    @Override
    public String buildLabel() {
        var targetLabel = mode == SonarMode.CLASSIC ? "" : (" " + target.name().toLowerCase());
        return "%s%s %s".formatted(mode.name().toLowerCase(), targetLabel, channel.name().toLowerCase());
    }
}
