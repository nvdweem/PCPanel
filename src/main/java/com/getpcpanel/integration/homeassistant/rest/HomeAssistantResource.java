package com.getpcpanel.integration.homeassistant.rest;

import java.util.List;
import java.util.Map;

import com.getpcpanel.integration.homeassistant.HomeAssistantService;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServerStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Connection status for the Home Assistant integration: the action editor's server picker and the
 * settings status dot. The server settings themselves are edited through the shared
 * {@code /api/settings} endpoint; actions are authored as pasted YAML, so there are no
 * entity/service list endpoints here (HA's own Developer Tools → Actions page builds those).
 */
@Path("/api/homeassistant")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class HomeAssistantResource {
    @Inject HomeAssistantService haService;

    @GET
    @Path("/servers")
    public List<HomeAssistantServerStatus> servers() {
        return haService.serverStatuses();
    }

    @GET
    @Path("/status")
    public Map<String, Boolean> status() {
        return Map.of("connected", haService.isAnyConnected());
    }
}
