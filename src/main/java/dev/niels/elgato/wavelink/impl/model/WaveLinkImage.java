package dev.niels.elgato.wavelink.impl.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record WaveLinkImage(
        @Nullable String name,
        @Nullable String imgData,
        @Nullable Boolean isAppIcon
) {
}
