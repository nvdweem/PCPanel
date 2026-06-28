package com.getpcpanel.integration.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Control your own Discord microphone (input) volume with a dial. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordSelfInputVolume")
public final class CommandDiscordSelfInputVolume extends CommandDiscord implements DialAction {
    @Nullable private final DialCommandParams dialParams;
    private final boolean unmuteOnChange;

    @JsonCreator
    public CommandDiscordSelfInputVolume(
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams,
            @JsonProperty("unmuteOnChange") boolean unmuteOnChange) {
        this.dialParams = dialParams;
        this.unmuteOnChange = unmuteOnChange;
    }

    @Override
    public String buildLabel() {
        return "Discord — mic volume";
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.applyInputVolume(context.dial().getValue(this, 0, 1), unmuteOnChange);
    }
}
