package dev.niels.elgato.wavelink.impl.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkEffect(
        String id,
        @Nullable String name,
        @Nullable Boolean isEnabled
) {
    public WaveLinkEffect blank() {
        return new WaveLinkEffect(id, null, null);
    }
}
