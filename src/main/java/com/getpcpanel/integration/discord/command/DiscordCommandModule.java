package com.getpcpanel.integration.discord.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Discord feature module: registers its own command types via the
 * {@link com.getpcpanel.commands.CommandModule} SPI. Adding/removing a command touches only this package.
 */
@ApplicationScoped
public class DiscordCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(
                CommandDiscordJoinVoice.class,
                CommandDiscordLeaveVoice.class,
                CommandDiscordMute.class,
                CommandDiscordScreenShare.class,
                CommandDiscordSelfDeafen.class,
                CommandDiscordSelfInputVolume.class,
                CommandDiscordSelfMute.class,
                CommandDiscordSelfOutputVolume.class,
                CommandDiscordToggleVideo.class,
                CommandDiscordUserMute.class,
                CommandDiscordUserVolume.class,
                CommandDiscordVolume.class);
    }
}
