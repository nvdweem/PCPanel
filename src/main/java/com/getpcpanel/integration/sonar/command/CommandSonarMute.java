package com.getpcpanel.integration.sonar.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.volume.platform.MuteType;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * {@code mute}/{@code unmute} are absolute, so they never need the current state. {@code toggle} needs
 * it to invert, and Sonar's per-route cache can genuinely not have it yet — {@link
 * com.getpcpanel.integration.sonar.SonarService#isReady()} only means a poll has produced a mode, not
 * that this exact channel/mix has been seen — so a toggle whose current state is unknown sends nothing
 * rather than guessing: {@link MuteType#convert} maps an unknown state to {@code true} for toggle, which
 * would be wrong half the time and silently leave an already-muted channel muted.
 *
 * <p>With {@link com.getpcpanel.integration.sonar.SonarMixSelection#both} the current state is muted only
 * when both mixes are, and unknown when either is: a toggle unmutes both when both are muted and
 * otherwise mutes both.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("sonar.mute")
@CommandMeta(label = "SteelSeries Sonar — mute", category = CommandCategory.integration, kinds = { CommandKind.button }, integration = "sonar", icon = "volume-x")
public final class CommandSonarMute extends CommandSonar implements ButtonAction {
    private final MuteType muteType;

    @JsonCreator
    public CommandSonarMute(
            @JsonProperty("channel") SonarChannel channel,
            @JsonProperty("mix") SonarMixSelection mix,
            @JsonProperty("muteType") MuteType muteType) {
        super(channel, mix);
        this.muteType = muteType;
    }

    @Override
    public String buildLabel() {
        return "(Un)Mute " + getChannel() + " (" + getMix().label() + ") — SteelSeries Sonar";
    }

    @Override
    public void execute() {
        var service = getSonarService();
        if (!service.isReady()) {
            log.debug("Not sending, Sonar is not ready");
            return;
        }
        var current = service.mutedOrNull(getChannel(), getMix());
        if (current == null && muteType == MuteType.toggle) {
            log.debug("Not sending, current mute state of {} ({}) is unknown", getChannel(), getMix());
            return;
        }
        service.setMute(getChannel(), getMix(), muteType.convert(current));
    }
}
