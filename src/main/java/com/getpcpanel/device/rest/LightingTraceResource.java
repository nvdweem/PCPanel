package com.getpcpanel.device.rest;

import com.getpcpanel.device.provider.pcpanel.LightingTrace;
import com.getpcpanel.rest.model.dto.LightingTraceRequestDto;
import com.getpcpanel.rest.model.dto.LightingTraceStateDto;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** The lighting trace switch and the user's "lights just went out" mark (Settings > Debug). */
@Path("/api/lighting-trace")
@Produces(MediaType.APPLICATION_JSON)
public class LightingTraceResource {
    static final String LIGHTS_OUT = "the user saw the lights go out";

    @Inject LightingTrace trace;

    @GET
    public LightingTraceStateDto state() {
        return new LightingTraceStateDto(trace.enabled(), trace.file());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public LightingTraceStateDto set(LightingTraceRequestDto request) {
        trace.setEnabled(request.enabled());
        return state();
    }

    @POST
    @Path("/mark")
    public Response markLightsOut() {
        trace.mark(LIGHTS_OUT);
        return Response.noContent().build();
    }
}
