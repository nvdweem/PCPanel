package com.getpcpanel.rest.discord.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A joinable Discord voice channel for the "join voice" command's picker.
 *
 * <p>Returned inside a {@code List} over REST, so both the record and its array form are registered for
 * native-image reflection (Jackson reflects over the element array per List).
 */
@RegisterForReflection(targets = { DiscordVoiceChannelDto.class, DiscordVoiceChannelDto[].class })
public record DiscordVoiceChannelDto(String id, String name, String guildName) {
}
