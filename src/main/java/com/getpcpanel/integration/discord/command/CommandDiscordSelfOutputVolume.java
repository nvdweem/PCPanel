package com.getpcpanel.integration.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Control your own Discord output (what you hear) volume with a dial. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume")
public final class CommandDiscordSelfOutputVolume extends CommandDiscord implements DialAction {
    @Nullable private final DialCommandParams dialParams;
    private final boolean undeafenOnChange;

    @JsonCreator
    public CommandDiscordSelfOutputVolume(
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams,
            @JsonProperty("undeafenOnChange") boolean undeafenOnChange) {
        this.dialParams = dialParams;
        this.undeafenOnChange = undeafenOnChange;
    }

    @Override
    public String buildLabel() {
        return "Discord — output volume";
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.applyOutputVolume(context.dial().getValue(this, 0, 1), undeafenOnChange);
    }
}
