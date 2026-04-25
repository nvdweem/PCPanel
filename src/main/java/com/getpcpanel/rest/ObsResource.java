package com.getpcpanel.rest;

import java.util.List;

import com.getpcpanel.obs.OBS;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/obs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ObsResource {
    @Inject OBS obs;

    @GET
    @Path("/scenes")
    public List<String> listScenes() {
        if (!obs.isConnected()) {
            return List.of();
        }
        return obs.getScenes();
    }

    @GET
    @Path("/sources")
    public List<String> listSources() {
        if (!obs.isConnected()) {
            return List.of();
        }
        return obs.getSourcesWithAudio();
    }
}
