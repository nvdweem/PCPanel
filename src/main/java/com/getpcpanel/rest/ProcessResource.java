package com.getpcpanel.rest;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.List;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
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
                      .map(app -> new ProcessDto(
                              app.pid(),
                              app.file().getAbsolutePath(),
                              app.name(),
                              encodeIcon(iconService.getIconForFile(32, 32, app.file()))))
                      .toList();
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
