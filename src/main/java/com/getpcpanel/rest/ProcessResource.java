package com.getpcpanel.rest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.rest.model.dto.ProcessDto;
import com.getpcpanel.util.image.PngEncoder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Path("/api/processes")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProcessResource {
    /** Distinct executables whose icon is kept; well above what a desktop runs, so the list is served from it. */
    private static final int ICON_CACHE_SIZE = 512;
    /** Stands for "this executable has no icon" in the cache, so it isn't asked again. */
    private static final String NO_ICON = "";

    @Inject ISndCtrl sndCtrl;
    @Inject IIconService iconService;

    /**
     * Encoded icons by executable and its modification time, least recently used dropped first. Many processes share
     * an executable and the list is read each time a picker opens; this way each executable goes through the shell
     * once.
     */
    private final Map<String, String> icons = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > ICON_CACHE_SIZE;
        }
    };

    @GET
    public List<ProcessDto> listProcesses() {
        // Opening the picker is the moment the list has to be right, so let the backend re-read it first
        // (a no-op where it is already live) instead of showing a stale snapshot — see #151.
        sndCtrl.refreshRunningApplications();
        return sndCtrl.getRunningApplications().stream()
                      .map(this::toDto)
                      .toList();
    }

    private ProcessDto toDto(ISndCtrl.RunningApplication app) {
        return new ProcessDto(app.pid(), app.file().getAbsolutePath(), app.name(), cachedIcon(app.file()));
    }

    @Nullable
    private String cachedIcon(File file) {
        var key = file.getAbsolutePath() + '|' + file.lastModified();
        synchronized (icons) {
            var cached = icons.get(key);
            if (cached != null) {
                return NO_ICON.equals(cached) ? null : cached;
            }
        }
        var icon = safeIcon(file);
        synchronized (icons) {
            icons.put(key, icon == null ? NO_ICON : icon);
        }
        return icon;
    }

    @Nullable
    private String safeIcon(File file) {
        // Icon extraction reaches Win32/GDI (IShellItemImageFactory, GetDIBits) and can throw for an
        // individual process — a zero-size bitmap, an inaccessible image, etc. One bad process must not
        // 500 the whole list (which would leave the UI's application picker empty until a page refresh),
        // so degrade to a null icon for that entry.
        try {
            return encodeIcon(iconService.getIconForFile(32, 32, file));
        } catch (Throwable t) {
            log.debug("Failed to extract icon for {}", file, t);
            return null;
        }
    }

    static String encodeIcon(BufferedImage img) {
        if (img == null) {
            return null;
        }
        // Encode with the pure-Java PngEncoder, not ImageIO (which loads the AWT Toolkit and crashes
        // the native image — see PngEncoder).
        var png = PngEncoder.encode(img);
        if (png == null) {
            log.debug("Failed to encode process icon");
            return null;
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
