package com.getpcpanel.rest;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/wavelink")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class WaveLinkResource {

    public static class WaveLinkChannel {
        public String id;
        public String name;

        public WaveLinkChannel(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class WaveLinkEffect {
        public String id;
        public String name;

        public WaveLinkEffect(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @GET
    @Path("/channels")
    public List<WaveLinkChannel> listChannels() {
        // Return empty list for now - these are typically obtained from WaveLink service
        return List.of();
    }

    @GET
    @Path("/effects")
    public List<WaveLinkEffect> listEffects() {
        // Return empty list for now - these are typically obtained from WaveLink service
        return List.of();
    }
}
