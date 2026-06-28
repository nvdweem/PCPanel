package com.getpcpanel.discord.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Toggle your camera (video) on/off in the current Discord call. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.discord.command.CommandDiscordToggleVideo")
public final class CommandDiscordToggleVideo extends CommandDiscord implements ButtonAction {
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordToggleVideo(@JsonProperty("overlayText") @Nullable String overlayText) {
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return "Discord — toggle camera";
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        service.toggleVideo().exceptionally(e -> {
            log.warn("Discord toggle video failed", e);
            return null;
        });
    }
}
