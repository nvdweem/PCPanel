package com.getpcpanel.commands.command.wavelink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.wavelink.WaveLinkService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@RequiredArgsConstructor
public class CommandWaveLinkLevel extends CommandWaveLink implements DialAction {
    private final WaveLinkCommand commandType;
    private Integer level;
    private Boolean mute;
    private DialCommandParams dialParams;

    @JsonCreator
    public CommandWaveLinkLevel(
            @JsonProperty("commandType") WaveLinkCommand commandType,
            @JsonProperty("id") String id,
            // @JsonProperty("level") Integer level,
            // @JsonProperty("mute") Boolean mute,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.commandType = commandType;
        level = level;
        mute = mute;
        this.dialParams = dialParams;
    }

    @Override
    public String buildLabel() {
        return "Set " + commandType +
                (level == null ? "" : " level: " + level) +
                (mute == null ? "" : " mute: " + mute);
    }

    @Override
    public void execute(DialActionParameters context) {
        MainFX.getBean(WaveLinkService.class).client.setChannel(
                "PCM_OUT_00_V_12_SD7",
                "PCM_IN_01_V_00_SD1",
                (double) context.dial().getValue(this, 0, 1),
                false
        );
    }

    public enum WaveLinkCommand {
        Input, Mix, Output
    }
}
