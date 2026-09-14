package com.getpcpanel.integration.discord;

import java.util.LinkedHashMap;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.discord.command.CommandDiscordMute;
import com.getpcpanel.integration.discord.command.CommandDiscordUserVolume;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import dev.niels.discord.model.DiscordVoiceUser;
import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ discord.… }}}: your own voice state and the members of your current voice channel (by username). */
@ApplicationScoped
public class DiscordTemplateNamespace implements TemplateNamespace {
    @Inject
    DiscordService discord;

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(discord, scope);
    }

    @TemplateData
    public static final class Root {
        private final DiscordService discord;
        private final TemplateScope scope;

        Root(DiscordService discord, TemplateScope scope) {
            this.discord = discord;
            this.scope = scope;
        }

        public boolean isConnected() {
            return discord.isAuthenticated();
        }

        @Nullable
        public Boolean getSelfMuted() {
            return discord.isAuthenticated() ? discord.getVoiceSettings().mute() : null;
        }

        @Nullable
        public Boolean getDeafened() {
            return discord.isAuthenticated() ? discord.getVoiceSettings().deaf() : null;
        }

        @Nullable
        public Integer getInputVolume() {
            return discord.isAuthenticated() ? discord.getVoiceSettings().inputVolume() : null;
        }

        @Nullable
        public Integer getOutputVolume() {
            return discord.isAuthenticated() ? discord.getVoiceSettings().outputVolume() : null;
        }

        public LazyMap<UserView> getUser() {
            var users = new LinkedHashMap<String, DiscordVoiceUser>();
            if (discord.isAuthenticated()) {
                discord.getVoiceUsers().forEach(u -> users.put(u.username(), u));
            }
            return LazyMap.of(users, name -> UserView.of(users.get(name)));
        }

        /** The member this control's Discord action acts on; your own state for a self mute. */
        @Nullable
        public UserView getTarget() {
            var commands = scope.commands();
            if (commands == null || !discord.isAuthenticated()) {
                return null;
            }
            var username = commands.getCommand(CommandDiscordMute.class).map(CommandDiscordMute::getTarget)
                                   .or(() -> commands.getCommand(CommandDiscordUserVolume.class).map(CommandDiscordUserVolume::getUsername))
                                   .orElse(null);
            if (username == null) {
                return null;
            }
            var self = discord.getSelfUser();
            if (StringUtils.isBlank(username) || CommandDiscordMute.SELF.equals(username) || self != null && username.equals(self.username())) {
                var settings = discord.getVoiceSettings();
                return new UserView(self == null ? "" : self.displayName(), settings.inputVolume(), settings.mute());
            }
            return discord.getVoiceUsers().stream().filter(u -> username.equals(u.username())).findFirst().map(UserView::of).orElse(null);
        }
    }

    @TemplateData
    public record UserView(String displayName, @Nullable Integer volume, boolean muted) {
        static UserView of(DiscordVoiceUser user) {
            return new UserView(user.displayName(), user.volume(), user.mute());
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
