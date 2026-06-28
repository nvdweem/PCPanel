package com.getpcpanel.integration.discord;

import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.discord.DiscordService;
import com.getpcpanel.integration.discord.command.CommandDiscordMute;
import com.getpcpanel.integration.discord.command.CommandDiscordSelfDeafen;
import com.getpcpanel.integration.discord.command.CommandDiscordSelfMute;
import com.getpcpanel.integration.discord.command.CommandDiscordUserMute;

import dev.niels.discord.model.DiscordVoiceUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of the Discord target a control's button drives, so its LED can show the mute-override
 * colour: self-mute follows your mic mute, self-deafen follows your deafen, and user-mute follows that
 * user's local mute. Discord pushes state as a {@code DiscordChangedEvent}, which {@link MuteColorService}
 * observes to recompute — so the colour tracks the live state however it changed (a dial, a button, or the
 * Discord client itself).
 */
@ApplicationScoped
public class DiscordMuteResolver implements MuteStateResolver {
    private final DiscordService discord;

    @Inject
    public DiscordMuteResolver(DiscordService discord) {
        this.discord = discord;
    }

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target) || !discord.isAuthenticated()) {
            return Optional.empty();
        }
        var mute = command.getCommand(CommandDiscordMute.class).orElse(null);
        if (mute != null) {
            var t = mute.getTarget();
            return StringUtils.isBlank(t) || CommandDiscordMute.SELF.equals(t)
                    ? Optional.of(discord.getVoiceSettings().mute())
                    : resolveUserMute(t);
        }
        if (command.getCommand(CommandDiscordSelfDeafen.class).isPresent()) {
            return Optional.of(discord.getVoiceSettings().deaf());
        }
        // Legacy commands kept so saves made before the mute/volume consolidation still drive the LED colour.
        if (command.getCommand(CommandDiscordSelfMute.class).isPresent()) {
            return Optional.of(discord.getVoiceSettings().mute());
        }
        var userMute = command.getCommand(CommandDiscordUserMute.class).orElse(null);
        return userMute == null ? Optional.empty() : resolveUserMute(userMute.getUsername());
    }

    private Optional<Boolean> resolveUserMute(@Nullable String username) {
        var id = discord.resolveUserId(username);
        if (id == null) {
            return Optional.empty();
        }
        var self = discord.getSelfUser();
        if (self != null && id.equals(self.id())) {
            return Optional.of(discord.getVoiceSettings().mute()); // user-mute of yourself behaves as self-mute
        }
        return discord.getVoiceUsers().stream().filter(u -> id.equals(u.id())).findFirst().map(DiscordVoiceUser::mute);
    }
}
