package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterBasic extends CommandVoiceMeeter implements DialAction {
    private final Voicemeeter.ControlType ct;
    private final int index;
    private final Voicemeeter.DialType dt;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandVoiceMeeterBasic(
            @JsonProperty("ct") Voicemeeter.ControlType ct,
            @JsonProperty("index") int index,
            @JsonProperty("dt") Voicemeeter.DialType dt,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.ct = ct;
        this.index = index;
        this.dt = dt;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        var voiceMeeter = MainFX.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlLevel(ct, index, dt, context.dial().getValue(this));
        }
    }

    @Override
    public String buildLabel() {
        return "Basic - " + ct + " - " + index + " - " + dt;
    }
}
