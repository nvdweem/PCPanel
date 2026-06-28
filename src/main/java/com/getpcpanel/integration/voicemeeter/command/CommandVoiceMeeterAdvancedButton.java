package com.getpcpanel.integration.voicemeeter.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

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
@JsonTypeName("voicemeeter.advanced-button")
@CommandMeta(label = "Voicemeeter — button", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "voicemeeter", icon = "sliders", legacyIds = {"com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton"})
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
        var voiceMeeter = CdiHelper.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlButton(fullParam, bt, stringValue);
        }
    }

    @Override
    public String buildLabel() {
        return "Advanced " + StringUtils.removeEnd(fullParam + " - " + bt + " - " + stringValue, " - ");
    }
}
