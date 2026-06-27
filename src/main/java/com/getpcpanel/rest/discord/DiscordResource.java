package com.getpcpanel.rest.discord;

import java.util.List;
import java.util.concurrent.CompletionStage;

import com.getpcpanel.discord.DiscordService;
import com.getpcpanel.rest.discord.dto.DiscordStatusDto;
import com.getpcpanel.rest.discord.dto.DiscordUserDto;
import com.getpcpanel.rest.discord.dto.DiscordVoiceChannelDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Live Discord data for the UI: the targetable user roster, the connection status, and the authorize trigger. */
@Path("/api/discord")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class DiscordResource {
    @Inject DiscordService discordService;

    @GET
    @Path("/users")
    public List<DiscordUserDto> users() {
        return DiscordUserDto.from(discordService);
    }

    @GET
    @Path("/status")
    public DiscordStatusDto status() {
        return DiscordStatusDto.from(discordService);
    }

    /** Joinable voice channels across the user's guilds, for the "join voice" command picker. */
    @GET
    @Path("/voice-channels")
    public CompletionStage<List<DiscordVoiceChannelDto>> voiceChannels() {
        return discordService.listVoiceChannels()
                .thenApply(list -> list.stream()
                        .map(c -> new DiscordVoiceChannelDto(c.id(), c.name(), c.guildName()))
                        .toList());
    }

    /**
     * Starts the interactive authorize flow and returns immediately (202). The flow shows a consent popup
     * inside Discord that the user must approve, which can take a while — so the request must NOT block on
     * it. The UI polls {@code /status} (and the flow fires a DiscordChangedEvent) to observe completion.
     */
    @POST
    @Path("/authorize")
    public Response authorize() {
        discordService.authorizeInteractive(); // fire-and-forget; progress is logged + status is polled
        return Response.accepted().build();
    }
}
