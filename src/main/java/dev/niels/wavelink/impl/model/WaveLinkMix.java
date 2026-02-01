package dev.niels.wavelink.impl.model;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record WaveLinkMix(
        String id,
        @Nullable String name,
        @Nullable Double level,
        @Nullable Boolean isMuted,
        @Nullable WaveLinkImage image
) implements WithId, Mergable<WaveLinkMix> {
    @Override
    public WaveLinkMix merge(@Nullable WaveLinkMix other) {
        if (other == null)
            return this;
        return new WaveLinkMix(
                ObjectUtils.firstNonNull(other.id, id),
                ObjectUtils.firstNonNull(other.name, name),
                ObjectUtils.firstNonNull(other.level, level),
                ObjectUtils.firstNonNull(other.isMuted, isMuted),
                ObjectUtils.firstNonNull(other.image, image)
        );
    }
}
