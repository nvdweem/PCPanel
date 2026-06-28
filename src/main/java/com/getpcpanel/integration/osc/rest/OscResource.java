package com.getpcpanel.integration.osc.rest;

import java.util.Map;

import com.getpcpanel.integration.osc.OSCService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Exposes the live OSC state (enabled + whether the listen socket is actually bound) to the UI. */
@Path("/api/osc")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class OscResource {
    @Inject OSCService oscService;

    @GET
    @Path("/status")
    public Map<String, Boolean> status() {
        return Map.of(
                "enabled", oscService.isEnabled(),
                "listening", oscService.isListening());
    }
}
