package com.getpcpanel.rest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import com.getpcpanel.iconextract.IIconService;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Path("/api/icons")
@ApplicationScoped
public class IconResource {
    @Inject IIconService iconService;

    @GET
    @Produces("image/png")
    public Response getIcon(@QueryParam("path") String filePath,
                            @QueryParam("size") @DefaultValue("32") int size) {
        if (filePath == null || filePath.isBlank()) {
            throw new NotFoundException();
        }
        // Resolve canonical path to prevent path traversal sequences (e.g. "../")
        File file;
        try {
            file = new File(filePath).getCanonicalFile();
        } catch (IOException e) {
            throw new NotFoundException();
        }
        // Restrict to files with known safe extensions to prevent arbitrary file access
        var name = file.getName().toLowerCase();
        var allowed = name.endsWith(".exe") || name.endsWith(".lnk") || name.endsWith(".ico")
                   || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                   || name.endsWith(".bmp") || name.endsWith(".gif");
        if (!allowed) {
            throw new NotFoundException();
        }
        if (!file.isFile()) {
            throw new NotFoundException();
        }
        var img = iconService.getIconForFile(size, size, file);
        if (img == null) {
            throw new NotFoundException();
        }
        try {
            var baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Response.ok(baos.toByteArray()).type("image/png").build();
        } catch (Exception e) {
            log.error("Failed to encode icon for {}", filePath, e);
            return Response.serverError().build();
        }
    }
}
