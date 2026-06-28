package com.getpcpanel.integration.discord.command;

import org.apache.commons.lang3.StringUtils;

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

/** Locally mute / unmute / toggle another Discord user (matched by username; only affects what you hear). */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordUserMute")
public final class CommandDiscordUserMute extends CommandDiscord implements ButtonAction {
    @Nullable private final String username;
    private final MuteType muteType;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordUserMute(
            @JsonProperty("username") @Nullable String username,
            @JsonProperty("muteType") @Nullable MuteType muteType,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.username = username;
        this.muteType = muteType == null ? MuteType.toggle : muteType;
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return StringUtils.isBlank(username)
                ? "Discord — " + DiscordCommandLabels.mute(muteType) + " user"
                : "Discord — " + DiscordCommandLabels.mute(muteType) + " " + username;
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        if (StringUtils.isBlank(username)) {
            log.warn("No Discord user specified");
            return;
        }
        service.toggleUserMute(username, muteType);
    }
}
