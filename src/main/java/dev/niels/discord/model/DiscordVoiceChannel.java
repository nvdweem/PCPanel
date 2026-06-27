package dev.niels.discord.model;

/** A joinable Discord voice channel, as listed over RPC (GET_GUILDS + GET_CHANNELS, type GUILD_VOICE). */
public record DiscordVoiceChannel(String id, String name, String guildId, String guildName) {
}
