package com.getpcpanel.integration.volume;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

import com.getpcpanel.integration.volume.overlay.Overlay;
import com.getpcpanel.integration.volume.overlay.OverlayPreviewRenderer;
import com.getpcpanel.profile.Save;
import com.getpcpanel.rest.model.dto.SettingsDto;
import com.sun.jna.Platform;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;

@Log4j2
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

    /** Font families the JVM/Java2D can actually render, so the overlay font picker only offers valid ones. */
    @GET
    @Path("/fonts")
    public List<String> fonts() {
        if (!Platform.isWindows()) {
            return List.of(); // overlay text only renders on Windows (headless Java2D)
        }
        try {
            return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                         .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (Throwable t) {
            log.warn("Could not enumerate fonts", t);
            return List.of();
        }
    }

    /**
     * The real overlay rendered to a PNG using the posted (possibly unsaved) settings, so the settings
     * page can show a pixel-identical live preview instead of a hand-maintained CSS mock. 204 when
     * rendering isn't available (non-Windows). The body is the same SettingsDto the settings form edits.
     */
    @POST
    @Path("/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("image/png")
    public Response preview(SettingsDto settings) {
        var save = new Save();
        settings.applyTo(save);
        var png = OverlayPreviewRenderer.renderPng(save, 65, "Microsoft Edge");
        if (png == null) {
            return Response.noContent().build();
        }
        return Response.ok(png).header("Cache-Control", "no-store").build();
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
