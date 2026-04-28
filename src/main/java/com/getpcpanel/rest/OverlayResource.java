package com.getpcpanel.rest;

import com.getpcpanel.overlay.Overlay;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/overlay")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class OverlayResource {
    @Inject Overlay overlay;

    @GET
    public Response testOverlay() {
        System.out.println("Overlay!");
        overlay.show(0);
        return Response.ok().build();
    }

    @POST
    public Response showOverlay(OverlayDto params) {
        overlay.show(params.value());
        return Response.ok().build();
    }

    @RegisterForReflection
    public record OverlayDto(int value, String icon) {
    }
}
