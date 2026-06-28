package com.getpcpanel.integration.voicemeeter.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("voicemeeter.advanced")
@CommandMeta(label = "Voicemeeter — parameter", category = CommandCategory.integration, kinds = {CommandKind.dial}, integration = "voicemeeter", icon = "sliders", legacyIds = {"com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced"})
public class CommandVoiceMeeterAdvanced extends CommandVoiceMeeter implements DialAction {
    private final String fullParam;
    private final Voicemeeter.DialControlMode ct;

    private final DialCommandParams dialParams;
    @JsonCreator
    public CommandVoiceMeeterAdvanced(
            @JsonProperty("fullParam") String fullParam,
            @JsonProperty("ct") Voicemeeter.DialControlMode ct,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.fullParam = fullParam;
        this.ct = ct;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        if (ct == null) {
            return;
        }
        var voiceMeeter = CdiHelper.getBean(Voicemeeter.class);
        if (voiceMeeter.login()) {
            voiceMeeter.controlLevel(fullParam, ct, context.dial().getValue(this));
        }
    }

    @Override
    public String buildLabel() {
        return "Advanced " + fullParam + " - " + ct;
    }
}
