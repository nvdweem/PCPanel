package com.getpcpanel.integration.voicemeeter.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton")
@CommandMeta(label = "Voicemeeter — strip or bus button", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "voicemeeter", icon = "sliders")
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
