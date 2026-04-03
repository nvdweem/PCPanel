package dev.niels.wavelink.impl.model;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.awt.image.BufferedImage;
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
        if (imgData != null && !imgData.isBlank()) {
            try {
                var imageBytes = Base64.getDecoder().decode(imgData);
                return new BufferedImage(new ByteArrayInputStream(imageBytes));
            } catch (IllegalArgumentException e) {
                log.debug("Unable to create image from image data {}", this);
            }
        }
        return null;
    }
}
