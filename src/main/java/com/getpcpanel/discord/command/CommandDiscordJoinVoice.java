package com.getpcpanel.discord.command;

import org.apache.commons.lang3.StringUtils;

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

/** Join (connect to) a specific Discord voice channel, chosen by id in the editor. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("discord.join-voice")
@CommandMeta(label = "Discord — join voice", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "discord", icon = "plug", legacyIds = {"com.getpcpanel.discord.command.CommandDiscordJoinVoice"})
public final class CommandDiscordJoinVoice extends CommandDiscord implements ButtonAction {
    @Nullable private final String channelId;
    /** Cosmetic: the channel's name at configure time, for the button label (the id is what we join). */
    @Nullable private final String channelName;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordJoinVoice(
            @JsonProperty("channelId") @Nullable String channelId,
            @JsonProperty("channelName") @Nullable String channelName,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return StringUtils.isBlank(channelName) ? "Discord — join voice" : "Discord — join " + channelName;
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        if (StringUtils.isBlank(channelId)) {
            log.warn("No Discord voice channel selected");
            return;
        }
        service.joinVoice(channelId);
    }
}
