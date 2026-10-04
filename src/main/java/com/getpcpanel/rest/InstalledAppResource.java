package com.getpcpanel.rest;

import java.io.File;
import java.time.Duration;
import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.integration.program.apps.InstalledApp;
import com.getpcpanel.integration.program.apps.InstalledAppScanner;
import com.getpcpanel.integration.program.apps.InstalledApps;
import com.getpcpanel.rest.model.dto.InstalledAppDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.log4j.Log4j2;

/** The installed apps, for the "Open app" action's picker. */
@Log4j2
@Path("/api/apps")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class InstalledAppResource {
    /** The list is a walk over the file system plus an icon per app, so an editor opened again soon reuses it. */
    private static final Duration CACHE_FOR = Duration.ofMinutes(1);

    private final InstalledAppScanner scanner;
    private final IIconService iconService;
    @Nullable private List<InstalledAppDto> cached;
    private long cachedAt;

    @Inject
    public InstalledAppResource(InstalledAppScanner scanner, IIconService iconService) {
        this.scanner = scanner;
        this.iconService = iconService;
    }

    @GET
    @Path("installed")
    public synchronized List<InstalledAppDto> listInstalled() {
        var now = System.nanoTime();
        if (cached == null || now - cachedAt > CACHE_FOR.toNanos()) {
            cached = InstalledApps.tidy(scanner.scan()).stream().map(this::toDto).toList();
            cachedAt = now;
        }
        return cached;
    }

    private InstalledAppDto toDto(InstalledApp app) {
        return new InstalledAppDto(app.name(), app.target(), app.exe(), icon(app));
    }

    /** Like the running-app list, one app whose icon cannot be read gets none rather than failing the list. */
    @Nullable
    private String icon(InstalledApp app) {
        try {
            return ProcessResource.encodeIcon(iconService.getIconForFile(32, 32, new File(app.iconFile())));
        } catch (Throwable t) {
            log.debug("Failed to extract icon for {}", app.iconFile(), t);
            return null;
        }
    }
}
