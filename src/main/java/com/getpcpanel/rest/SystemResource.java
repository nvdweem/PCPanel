package com.getpcpanel.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.getpcpanel.rest.model.dto.OnboardingDto;
import com.getpcpanel.util.app.StartupOnboarding;
import com.getpcpanel.util.version.AutoUpdateService;
import com.getpcpanel.util.version.AutoUpdateService.UpdateException;

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
    @Inject StartupOnboarding onboarding;
    @Inject AutoUpdateService autoUpdate;

    /**
     * One-time onboarding hint for the UI (which welcome/update dialog to show, the version, and the
     * changelog link). The UI fetches this once on load.
     */
    @GET
    @Path("/onboarding")
    public OnboardingDto onboarding() {
        return onboarding.info();
    }

    /** Mark the onboarding dialog as shown so it does not reappear on refresh. */
    @POST
    @Path("/onboarding/ack")
    public Response acknowledgeOnboarding() {
        onboarding.acknowledge();
        return Response.noContent().build();
    }

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

    /**
     * Windows self-update: download the latest release's installer and run it silently, then restart.
     * Only works on an installed Windows build ({@link AutoUpdateService#isSupported()}); elsewhere the
     * UI links to the release page instead and never calls this. The running app is closed by the
     * installer moments after it is launched, so the response is returned first.
     */
    @POST
    @Path("/update")
    public Response update() {
        return runUpdate(autoUpdate::updateToLatest);
    }

    /** Debug/testing: re-download and reinstall the currently running version through the same path. */
    @POST
    @Path("/update/reinstall")
    public Response reinstallCurrent() {
        return runUpdate(autoUpdate::reinstallCurrent);
    }

    private Response runUpdate(UpdateAction action) {
        try {
            var target = action.run();
            log.info("Update to {} started from the web UI", target.version());
            return Response.accepted().entity(target).build();
        } catch (UpdateException e) {
            log.warn("Update could not start: {}", e.getMessage());
            return Response.status(Response.Status.CONFLICT).entity(new ErrorDto(e.getMessage())).build();
        } catch (Exception e) {
            log.error("Update failed to start", e);
            return Response.serverError().entity(new ErrorDto("The update could not be started: " + e.getMessage())).build();
        }
    }

    @FunctionalInterface
    private interface UpdateAction {
        AutoUpdateService.UpdateTarget run() throws Exception;
    }

    @io.quarkus.runtime.annotations.RegisterForReflection
    public record ErrorDto(String error) {}
}
