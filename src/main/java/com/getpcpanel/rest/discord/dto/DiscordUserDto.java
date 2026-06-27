package com.getpcpanel.rest.discord.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.discord.DiscordService;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A Discord user the command editor can target, by username. The list unions the members of the voice
 * channel you are currently in ({@code inVoice = true}) with the persisted "seen users" roster, so a
 * username is pickable even when you're not in a call.
 *
 * <p>Returned inside a {@code List} over REST, so both the record and its array form are registered for
 * native-image reflection (Jackson reflects over the element array per List).
 */
@RegisterForReflection(targets = { DiscordUserDto.class, DiscordUserDto[].class })
public record DiscordUserDto(String id, String username, String displayName, boolean inVoice) {
    public static List<DiscordUserDto> from(DiscordService service) {
        // Exclude yourself: the "user" voice commands only work on OTHER members (Discord rejects your own id);
        // to control your own mic/output use the Self commands. Keeping yourself out of the picker stops that trap.
        var self = service.getSelfUser();
        var selfId = self == null ? null : self.id();
        var byUsername = new LinkedHashMap<String, DiscordUserDto>();
        for (var u : service.getVoiceUsers()) {
            if (StringUtils.isBlank(u.username()) || StringUtils.equals(u.id(), selfId)) {
                continue;
            }
            byUsername.put(u.username().toLowerCase(Locale.ROOT), new DiscordUserDto(u.id(), u.username(), u.displayName(), true));
        }
        for (var u : service.getSeenUsers()) {
            if (StringUtils.isBlank(u.username()) || StringUtils.equals(u.id(), selfId)) {
                continue;
            }
            byUsername.putIfAbsent(u.username().toLowerCase(Locale.ROOT), new DiscordUserDto(u.id(), u.username(), u.displayName(), false));
        }
        return List.copyOf(byUsername.values());
    }
}
