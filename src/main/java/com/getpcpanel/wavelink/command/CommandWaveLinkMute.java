package com.getpcpanel.wavelink.command;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.MuteType;

import io.reactivex.annotations.Nullable;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandWaveLinkMute extends CommandWaveLink implements ButtonAction {
    private final MuteType muteType;

    @JsonCreator
    public CommandWaveLinkMute(
            @JsonProperty("commandType") WaveLinkCommandTarget commandType,
            @JsonProperty("id1") @Nullable String id1,
            @JsonProperty("id2") @Nullable String id2,
            @JsonProperty("muteType") MuteType muteType) {
        super(commandType, id1, id2);
        this.muteType = muteType;
    }

    @Override
    public String buildLabel() {
        return "(Un)Mute " + getCommandType();
    }

    @Override
    public void execute() {
        var service = getWaveLinkService();
        if (!service.isConnected()) {
            log.warn("Not sending command, wavelink not connected");
            return;
        }
        if (StringUtils.isBlank(getId1())) {
            log.warn("No id specified");
            return;
        }

        switch (getCommandType()) {
            case Input -> log.warn("Input mute not supported yet");
            case Channel -> {
                var channel = service.getChannelFromId(getId1());
                service.setChannelMute(getId1(), muteType.convert(channel.isMuted()));
            }
            case Mix -> {
                if (StringUtils.isBlank(getId2())) {
                    log.warn("No mix id specified");
                    return;
                }
                var channel = service.getChannelFromId(getId1());
                var mix = channel.findMix(getId2()).orElseGet(() -> service.getMixFromId(getId2()));
                service.setChannelMute(channel, mix, muteType.convert(mix.isMuted()));
            }
            case Output -> {
                var output = service.getOutputFromId(getId1());
                if (!CollectionUtils.isEmpty(output.outputs())) {
                    var newMuted = muteType.convert(output.outputs().getFirst().isMuted());
                    service.setOutputMute(getId1(), newMuted);
                } else {
                    log.warn("No outputs found for output {}", getId1());
                }
            }
        }
    }
}
