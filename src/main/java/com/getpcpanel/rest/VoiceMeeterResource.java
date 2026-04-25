package com.getpcpanel.rest;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/voicemeeter")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class VoiceMeeterResource {

    public static class VoiceMeeterParam {
        public String name;
        public List<String> params;

        public VoiceMeeterParam(String name, List<String> params) {
            this.name = name;
            this.params = params;
        }
    }

    @GET
    @Path("/basic")
    public List<VoiceMeeterParam> getBasicParams() {
        // Return empty list for now - these are typically obtained from user configuration
        return List.of();
    }

    @GET
    @Path("/advanced")
    public List<VoiceMeeterParam> getAdvancedParams() {
        // Return empty list for now - these are typically obtained from user configuration
        return List.of();
    }
}
