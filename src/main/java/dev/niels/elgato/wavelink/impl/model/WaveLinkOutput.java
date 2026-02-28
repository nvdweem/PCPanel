package dev.niels.elgato.wavelink.impl.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkOutput(
        String id,
        @Nullable String name,
        @Nullable Boolean isMuted,
        @Nullable Double level,
        @Nullable String mixId
) implements WithId {
    public WaveLinkOutput blank() {
        return new WaveLinkOutput(id, null, null, null, null);
    }
}
