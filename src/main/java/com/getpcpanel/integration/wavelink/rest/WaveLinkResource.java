package com.getpcpanel.integration.wavelink.rest;

import com.getpcpanel.integration.wavelink.rest.dto.WaveLinkResponseDto;
import com.getpcpanel.integration.wavelink.WaveLinkService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/wavelink")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class WaveLinkResource {
    @Inject WaveLinkService waveLinkService;

    @GET
    @Path("/devices")
    public WaveLinkResponseDto listAllDevices() {
        return WaveLinkResponseDto.from(waveLinkService);
    }
}
