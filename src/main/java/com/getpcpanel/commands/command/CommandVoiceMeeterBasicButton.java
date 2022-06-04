package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterBasicButton extends CommandVoiceMeeter implements ButtonAction {
    private final Voicemeeter.ControlType ct;
    private final int index;
    private final Voicemeeter.ButtonType bt;

    @JsonCreator
    public CommandVoiceMeeterBasicButton(@JsonProperty("ct") Voicemeeter.ControlType ct, @JsonProperty("index") int index, @JsonProperty("bt") Voicemeeter.ButtonType bt) {
        this.ct = ct;
        this.index = index;
        this.bt = bt;
    }

    @Override
    public void execute() {
        if (Voicemeeter.login()) {
            Voicemeeter.controlButton(ct, index, bt);
        }
    }
}
