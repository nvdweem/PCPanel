package com.getpcpanel.rest;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Reports the host platform so the UI can hide platform-specific integrations, plus the running
 * app version (so the UI doesn't hardcode it). This must come from the backend (where the device +
 * integrations actually run), not the browser — the UI may be opened from another machine on the
 * network. Voicemeeter is Windows-only; Elgato Wave Link is Windows/macOS only.
 */
@Path("/api/platform")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PlatformResource {

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    public record PlatformInfo(String os, boolean voicemeeter, boolean waveLink, String version) {
    }

    @GET
    public PlatformInfo get() {
        var os = SystemUtils.IS_OS_WINDOWS ? "windows" : SystemUtils.IS_OS_MAC ? "mac" : SystemUtils.IS_OS_LINUX ? "linux" : "other";
        return new PlatformInfo(os, SystemUtils.IS_OS_WINDOWS, SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC, version);
    }
}
