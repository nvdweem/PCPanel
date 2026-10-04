package com.getpcpanel.integration.display.rest;

import java.util.List;

import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.integration.display.rest.dto.DisplayDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/displays")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class DisplayResource {
    @Inject DisplayPower displayPower;

    /** The monitors that can be chosen for Turn displays off (DDC/CI); empty where there are none. */
    @GET
    public List<DisplayDto> list() {
        return displayPower.list().stream().map(d -> new DisplayDto(d.id(), d.name())).toList();
    }
}
