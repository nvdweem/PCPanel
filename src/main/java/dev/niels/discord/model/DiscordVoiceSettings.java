package dev.niels.discord.model;

import javax.annotation.Nullable;

/**
 * The local user's own voice settings (SET_VOICE_SETTINGS / GET_VOICE_SETTINGS). {@code mute} and
 * {@code deaf} are self mute/deafen; the volumes are the input (mic, 0-100) and output (0-200) levels,
 * null when not reported.
 */
public record DiscordVoiceSettings(boolean mute, boolean deaf, @Nullable Integer inputVolume, @Nullable Integer outputVolume) {
    public static final DiscordVoiceSettings EMPTY = new DiscordVoiceSettings(false, false, null, null);
}
