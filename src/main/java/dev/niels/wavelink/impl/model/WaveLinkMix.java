package dev.niels.wavelink.impl.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkMix(
        String id,
        @Nullable String name,
        @Nullable Double level,
        @Nullable Boolean isMuted,
        @Nullable WaveLinkImage image
) implements WithId {
    public WaveLinkMix blank() {
        return new WaveLinkMix(id, null, null, null, null);
    }
}
