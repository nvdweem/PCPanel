package dev.niels.discord.model;

import org.apache.commons.lang3.StringUtils;

/**
 * A member of the voice channel the local user is currently in, with the local client-side voice
 * settings for that member ({@code volume} 0-200 and {@code mute} = locally muted in <em>your</em>
 * client, set via SET_USER_VOICE_SETTINGS). {@code nick} is the per-guild nickname when present.
 */
public record DiscordVoiceUser(String id, String username, String globalName, String nick, int volume, boolean mute) {
    /** Best human-facing label: guild nick, else global display name, else username, else id. */
    public String displayName() {
        return StringUtils.firstNonBlank(nick, globalName, username, id);
    }
}
