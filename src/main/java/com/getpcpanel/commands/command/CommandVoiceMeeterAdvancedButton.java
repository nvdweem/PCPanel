package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterAdvancedButton extends CommandVoiceMeeter implements ButtonAction {
    private final String fullParam;
    private final Voicemeeter.ButtonControlMode bt;

    @JsonCreator
    public CommandVoiceMeeterAdvancedButton(@JsonProperty("fullParam") String fullParam, @JsonProperty("bt") Voicemeeter.ButtonControlMode bt) {
        this.fullParam = fullParam;
        this.bt = bt;
    }

    @Override
    public void execute() {
        if (Voicemeeter.login()) {
            Voicemeeter.controlButton(fullParam, bt);
        }
    }
}
