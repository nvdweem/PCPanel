package com.getpcpanel.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.runtime.Quarkus;
import lombok.extern.log4j.Log4j2;

/**
 * Application lifecycle control exposed to the web UI. PCPanel runs headless (a tray app on
 * Windows/Linux, nothing visible on macOS), so the browser UI is the one place every platform can
 * quit it from without resorting to a terminal — see #104 (macOS has no tray) and #100 (the Linux
 * tray had no Quit entry).
 */
@Log4j2
@Path("/api/system")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class SystemResource {

    /**
     * Shuts the application down. Uses {@link Quarkus#asyncExit()} so this request can return its
     * response before the JVM stops; the normal Quarkus shutdown sequence then runs (ShutdownEvent →
     * device handlers close, tray icon is removed, AppShutdownState flips), exactly as a tray "Exit"
     * would. The UI shows a "stopped" state afterwards since the server is then gone.
     */
    @POST
    @Path("/quit")
    public Response quit() {
        log.info("Quit requested from the web UI; shutting down");
        Quarkus.asyncExit(0);
        return Response.accepted().build();
    }
}
