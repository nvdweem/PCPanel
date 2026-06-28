package com.getpcpanel.integration.osc.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.integration.osc.OSCService;
import com.getpcpanel.commands.command.CommandValueOutput;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Sends an OSC message (a single float argument) to {@code address} on every configured OSC send
 * target. The argument is the dial-mapped value (or the configured max on a button press). Requires
 * OSC to be enabled with at least one send target in settings.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("osc.send")
@CommandMeta(label = "OSC send", category = CommandCategory.system, kinds = {CommandKind.dial, CommandKind.button}, icon = "sliders", legacyIds = {"com.getpcpanel.commands.command.CommandOscSend"})
public class CommandOscSend extends CommandValueOutput {
    private final String address;

    @JsonCreator
    public CommandOscSend(
            @JsonProperty("address") String address,
            @JsonProperty("min") @Nullable Double min,
            @JsonProperty("max") @Nullable Double max,
            @JsonProperty("formula") @Nullable String formula,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(min, max, formula, dialParams);
        this.address = address;
    }

    @Override
    protected void send(double value, boolean immediate) {
        // UDP and cheap, so dial streams send every event rather than being rate-limited.
        CdiHelper.getBean(OSCService.class).send(address, (float) value);
    }

    @Override
    public String buildLabel() {
        return "OSC: " + StringUtils.defaultString(address);
    }
}
