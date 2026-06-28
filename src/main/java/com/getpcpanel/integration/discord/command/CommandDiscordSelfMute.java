package com.getpcpanel.integration.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.discord.DiscordCommandLabels;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Mute / unmute / toggle your own microphone in Discord. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordSelfMute")
public final class CommandDiscordSelfMute extends CommandDiscord implements ButtonAction {
    private final MuteType muteType;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordSelfMute(
            @JsonProperty("muteType") @Nullable MuteType muteType,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.muteType = muteType == null ? MuteType.toggle : muteType;
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return "Discord — " + DiscordCommandLabels.mute(muteType) + " self";
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.toggleSelfMute(muteType);
    }
}
