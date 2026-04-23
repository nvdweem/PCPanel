package com.getpcpanel.rest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.rest.model.dto.ProcessDto;

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
        try {
            var baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.debug("Failed to encode process icon", e);
            return null;
        }
    }
}
