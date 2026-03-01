package com.getpcpanel.elgato.wavelink.command;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.MuteType;

import dev.niels.elgato.wavelink.impl.model.WaveLinkEffect;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandWaveLinkChannelEffect extends CommandWaveLink implements ButtonAction {
    @Nullable private final String channelId;
    @Nullable private final String channelName;
    @Nullable private final String effectId;
    @Nullable private final String effectName;
    private final MuteType toggleType;

    @JsonCreator
    public CommandWaveLinkChannelEffect(
            @JsonProperty("channelId") @Nullable String channelId,
            @JsonProperty("channelName") @Nullable String channelName,
            @JsonProperty("effectId") @Nullable String effectId,
            @JsonProperty("effectName") @Nullable String effectName,
            @JsonProperty("toggleType") MuteType toggleType) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.effectId = effectId;
        this.effectName = effectName;
        this.toggleType = toggleType;
    }

    @Override
    public String buildLabel() {
        return "Effect " + channelName + " - " + effectName;
    }

    @Override
    public void execute() {
        var service = getWaveLinkService();
        var channel = service.getChannelFromId(channelId);
        var effects = Objects.requireNonNullElseGet(channel.effects(), List::<WaveLinkEffect>of);
        var newChannel = channel.blank()
                                .withEffects(
                                        StreamEx.of(effects)
                                                .filterBy(WaveLinkEffect::id, effectId)
                                                .map(eff -> {
                                                    var current = eff.isEnabled();
                                                    return eff.blank().withIsEnabled(toggleType.convert(current));
                                                })
                                                .toList());
        service.setChannel(newChannel);
    }
}
