package com.getpcpanel.device.rest;

import com.getpcpanel.device.PanelTestService;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/** "Test my panel" and the start-up animation preview. */
@Path("/api/panel-test")
public class PanelTestResource {
    @Inject PanelTestService panelTest;

    @POST
    @Path("/{serial}/start")
    public Response start(@PathParam("serial") String serial) {
        return panelTest.start(serial) ? Response.ok().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/{serial}/lights")
    public Response lights(@PathParam("serial") String serial) {
        panelTest.testLights(serial);
        return Response.ok().build();
    }

    @POST
    @Path("/{serial}/stop")
    public Response stop(@PathParam("serial") String serial) {
        panelTest.stop(serial);
        return Response.ok().build();
    }

    @POST
    @Path("/startup-animation")
    public Response startupAnimation() {
        panelTest.playStartupAnimation();
        return Response.ok().build();
    }
}
