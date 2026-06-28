package com.getpcpanel.integration.program;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Base64;
import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.program.iconextract.IIconService;
import com.getpcpanel.rest.model.dto.ProcessDto;
import com.getpcpanel.util.PngEncoder;

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
    @Inject ISndCtrl sndCtrl;
    @Inject IIconService iconService;

    @GET
    public List<ProcessDto> listProcesses() {
        return sndCtrl.getRunningApplications().stream()
                      .map(this::toDto)
                      .toList();
    }

    private ProcessDto toDto(ISndCtrl.RunningApplication app) {
        return new ProcessDto(app.pid(), app.file().getAbsolutePath(), app.name(), safeIcon(app.file()));
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
