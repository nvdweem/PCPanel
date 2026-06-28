package com.getpcpanel.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.discord.DiscordCommandLabels;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Deafen / undeafen / toggle yourself in Discord (deafening also mutes your own output). */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordSelfDeafen")
public final class CommandDiscordSelfDeafen extends CommandDiscord implements ButtonAction {
    private final MuteType muteType;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordSelfDeafen(
            @JsonProperty("muteType") @Nullable MuteType muteType,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.muteType = muteType == null ? MuteType.toggle : muteType;
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return "Discord — " + DiscordCommandLabels.deafen(muteType);
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.toggleSelfDeafen(muteType);
    }
}
