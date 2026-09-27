package com.getpcpanel.integration.sonar.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.SonarService;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("sonar.volume")
@CommandMeta(label = "SteelSeries Sonar — volume", category = CommandCategory.integration, kinds = { CommandKind.dial }, integration = "sonar", icon = "sliders")
public final class CommandSonarVolume extends CommandSonar implements DialAction {
    private final boolean unMuteOnVolumeChange;
    @Nullable private final DialCommandParams dialParams;

    @JsonCreator
    public CommandSonarVolume(
            @JsonProperty("channel") SonarChannel channel,
            @JsonProperty("mix") SonarMixSelection mix,
            @JsonProperty("unMuteOnVolumeChange") boolean unMuteOnVolumeChange,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(channel, mix);
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
        this.dialParams = dialParams;
    }

    @Override
    public String buildLabel() {
        return getChannel() + " (" + getMix().label() + ")" + (unMuteOnVolumeChange ? " (unmute)" : "") + " — SteelSeries Sonar";
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getSonarService();
        if (!service.isReady()) {
            log.debug("Not sending, Sonar is not ready");
            return;
        }
        if (!context.initial() && unMuteOnVolumeChange) {
            unmuteMutedMixes(service);
        }
        // Already curve-, trim- and invert-adjusted, and Sonar wants 0..1 — so no scaling of our own.
        var value = (double) context.dial().getValue(this, 0, 1);
        service.setVolume(getChannel(), getMix(), value);
    }

    /**
     * Unmutes only a mix known to be muted, so a sweep sends no mute write per tick: the unmute updates the
     * cache at once, and an unread mix is left alone rather than guessed at. Checked per mix, since
     * {@link SonarMixSelection#both} reads as muted only when both mixes are. In Classic mode both mixes are
     * one route, so the second check already sees the first unmute.
     */
    private void unmuteMutedMixes(SonarService service) {
        for (var mix : getMix().mixes()) {
            if (Boolean.TRUE.equals(service.mutedOrNull(getChannel(), mix))) {
                service.setMute(getChannel(), mix, false);
            }
        }
    }
}
