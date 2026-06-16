package dev.niels.wavelink.impl.model;

import java.awt.image.BufferedImage;
import java.util.Base64;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.getpcpanel.util.PngDecoder;

import lombok.extern.log4j.Log4j2;

@Log4j2
@JsonInclude(Include.NON_NULL)
public record WaveLinkImage(
        @Nullable String name,
        @Nullable String imgData,
        @Nullable Boolean isAppIcon
) {
    @Nullable
    public BufferedImage getImage() {
        if (imgData == null || imgData.isBlank()) {
            return null;
        }
        try {
            // Decode with the pure-Java PngDecoder, NOT ImageIO. ImageIO's static initialization reaches
            // java.awt.Toolkit, whose native library (awt.dll) cannot be loaded in the GraalVM native image
            // — even on Windows, where headless Java2D is otherwise available — and throws
            // UnsatisfiedLinkError, killing the input thread when an overlay icon is rendered. Wave Link
            // sends PNG image data, which PngDecoder handles without touching AWT/Toolkit.
            return PngDecoder.decode(Base64.getDecoder().decode(imgData));
        } catch (IllegalArgumentException e) {
            log.debug("Unable to create image from image data {}", this);
            return null;
        }
    }
}
