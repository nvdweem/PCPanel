package dev.niels.wavelink.impl.model;

import java.util.Base64;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.extern.log4j.Log4j2;

@Log4j2
@JsonInclude(Include.NON_NULL)
public record WaveLinkImage(
        @Nullable String name,
        @Nullable String imgData,
        @Nullable Boolean isAppIcon
) {
    /**
     * The image's raw bytes as Wave Link sent them (PNG), or null when it carries none or the data is not
     * valid base64. Decoding them into an image is up to the caller.
     */
    @Nullable
    public byte[] pngBytes() {
        if (imgData == null || imgData.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(imgData);
        } catch (IllegalArgumentException e) {
            log.debug("Image data of {} is not valid base64", name);
            return null;
        }
    }
}
