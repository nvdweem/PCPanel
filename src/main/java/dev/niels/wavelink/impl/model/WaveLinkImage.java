package dev.niels.wavelink.impl.model;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import javafx.scene.image.Image;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@JsonInclude(Include.NON_NULL)
public record WaveLinkImage(
        @Nullable String name,
        @Nullable String imgData,
        @Nullable Boolean isAppIcon
) {
    @Nullable
    public Image getImage() {
        if (imgData != null && !imgData.isBlank()) {
            try {
                var imageBytes = Base64.getDecoder().decode(imgData);
                return new Image(new ByteArrayInputStream(imageBytes));
            } catch (IllegalArgumentException e) {
                log.debug("Unable to create image from image data {}", this);
            }
        }
        return null;
    }
}
