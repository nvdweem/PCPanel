package com.getpcpanel.voicemeeter.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton")
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
        var voiceMeeter = CdiHelper.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlButton(ct, index, bt, null);
        }
    }

    @Override
    public String buildLabel() {
        return "Basic " + ct + " - " + index + " - " + (bt == null ? "-" : bt.getParameterName());
    }
}
