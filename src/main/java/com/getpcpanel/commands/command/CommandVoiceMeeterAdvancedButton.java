package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterAdvancedButton extends CommandVoiceMeeter implements ButtonAction {
    private final String fullParam;
    private final Voicemeeter.ButtonControlMode bt;
    private final String stringValue;

    @JsonCreator
    public CommandVoiceMeeterAdvancedButton(
            @JsonProperty("fullParam") String fullParam,
            @JsonProperty("bt") Voicemeeter.ButtonControlMode bt,
            @Nullable @JsonProperty("stringValue") String stringValue) {
        this.fullParam = fullParam;
        this.bt = bt;
        this.stringValue = stringValue;
    }

    @Override
    public void execute() {
        var voiceMeeter = MainFX.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlButton(fullParam, bt, stringValue);
        }
    }
}
