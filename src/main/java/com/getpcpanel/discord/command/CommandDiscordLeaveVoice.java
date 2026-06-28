package com.getpcpanel.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Disconnect from the current Discord voice channel. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("discord.leave-voice")
@CommandMeta(label = "Discord — leave voice", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "discord", icon = "log-out", legacyIds = {"com.getpcpanel.discord.command.CommandDiscordLeaveVoice"})
public final class CommandDiscordLeaveVoice extends CommandDiscord implements ButtonAction {
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordLeaveVoice(@JsonProperty("overlayText") @Nullable String overlayText) {
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return "Discord — leave voice";
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.leaveVoice();
    }
}
