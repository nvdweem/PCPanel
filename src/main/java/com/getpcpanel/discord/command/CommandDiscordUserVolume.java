package com.getpcpanel.discord.command;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Control how loudly you hear another Discord user (matched by username) with a dial — 0..200%. */
@Getter
@Log4j2
@ToString(callSuper = true)
public final class CommandDiscordUserVolume extends CommandDiscord implements DialAction {
    @Nullable private final String username;
    @Nullable private final DialCommandParams dialParams;

    @JsonCreator
    public CommandDiscordUserVolume(
            @JsonProperty("username") @Nullable String username,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        this.username = username;
        this.dialParams = dialParams;
    }

    @Override
    public String buildLabel() {
        return StringUtils.isBlank(username) ? "Discord — user volume" : "Discord — " + username + " volume";
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        if (StringUtils.isBlank(username)) {
            log.warn("No Discord user specified");
            return;
        }
        service.applyUserVolume(username, context.dial().getValue(this, 0, 1));
    }
}
