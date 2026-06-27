package com.getpcpanel.discord.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.discord.DiscordService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Base for the Discord voice-control commands. Resolves the {@link DiscordService} lazily via
 * {@link CdiHelper} so a deserialised command stays a plain data object (never holds a bean reference).
 */
@Getter
@ToString(callSuper = true)
public abstract sealed class CommandDiscord extends Command
        permits CommandDiscordMute, CommandDiscordVolume, CommandDiscordScreenShare,
        CommandDiscordSelfMute, CommandDiscordSelfDeafen, CommandDiscordUserMute,
        CommandDiscordSelfInputVolume, CommandDiscordSelfOutputVolume, CommandDiscordUserVolume,
        CommandDiscordJoinVoice, CommandDiscordLeaveVoice {
    protected DiscordService getDiscordService() {
        return CdiHelper.getBean(DiscordService.class);
    }
}
