package com.getpcpanel.rest.discord;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.getpcpanel.discord.DiscordService;
import com.getpcpanel.rest.discord.dto.DiscordStatusDto;
import com.getpcpanel.rest.discord.dto.DiscordUserDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;

/** Live Discord data for the UI: the targetable user roster, the connection status, and the authorize trigger. */
@Log4j2
@Path("/api/discord")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class DiscordResource {
    /** The user has to approve the consent popup inside Discord, so allow generous time. */
    private static final long AUTHORIZE_TIMEOUT_SECONDS = 120;

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

    /**
     * Runs the interactive authorize flow (Discord shows a consent popup; the user approves it), blocking
     * until it settles so the UI can show the result. Returns the resulting status.
     */
    @POST
    @Path("/authorize")
    public DiscordStatusDto authorize() {
        try {
            discordService.authorizeInteractive().get(AUTHORIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new WebApplicationException("Timed out waiting for Discord authorization (approve the popup in Discord).", Response.Status.GATEWAY_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Discord authorization failed: " + rootMessage(e), Response.Status.BAD_GATEWAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebApplicationException("Discord authorization interrupted", Response.Status.SERVICE_UNAVAILABLE);
        }
        return DiscordStatusDto.from(discordService);
    }

    private static String rootMessage(Throwable e) {
        var cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
