package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterAdvanced extends CommandVoiceMeeter implements DialAction {
    private final String fullParam;
    private final Voicemeeter.DialControlMode ct;
    private final boolean invert;

    @JsonCreator
    public CommandVoiceMeeterAdvanced(
            @JsonProperty("fullParam") String fullParam,
            @JsonProperty("ct") Voicemeeter.DialControlMode ct,
            @JsonProperty("isInvert") boolean invert) {
        this.fullParam = fullParam;
        this.ct = ct;
        this.invert = invert;
    }

    @Override
    public void execute(DialActionParameters context) {
        var voiceMeeter = MainFX.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlLevel(fullParam, ct, context.dial());
        }
    }
}
