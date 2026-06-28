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
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.discord.DiscordCommandLabels;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Mute / unmute / toggle in Discord. {@code target} is {@link #SELF} (or blank) to mute your own mic, or
 * another member's username to locally mute them (only affects what you hear).
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("discord.mute")
@CommandMeta(label = "Discord — mute", category = CommandCategory.integration, kinds = {CommandKind.button}, integration = "discord", icon = "mic-off", legacyIds = {"com.getpcpanel.discord.command.CommandDiscordMute"})
public final class CommandDiscordMute extends CommandDiscord implements ButtonAction {
    public static final String SELF = "self";

    @Nullable private final String target;
    private final MuteType muteType;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordMute(
            @JsonProperty("target") @Nullable String target,
            @JsonProperty("muteType") @Nullable MuteType muteType,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.target = target;
        this.muteType = muteType == null ? MuteType.toggle : muteType;
        this.overlayText = overlayText;
    }

    private boolean isSelf() {
        return StringUtils.isBlank(target) || SELF.equals(target);
    }

    @Override
    public String buildLabel() {
        return "Discord — " + DiscordCommandLabels.mute(muteType) + " " + (isSelf() ? "self" : target);
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        if (isSelf()) {
            service.toggleSelfMute(muteType);
        } else {
            service.toggleUserMute(target, muteType);
        }
    }
}
