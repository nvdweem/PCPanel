package dev.niels.wavelink.impl.model;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkChannel(
        String id,
        @Nullable String name,
        @Nullable String type,
        @Nullable List<WaveLinkMix> mixes,
        @Nullable Double level,
        @Nullable Boolean isMuted,
        @Nullable List<WaveLinkApp> apps,
        @Nullable List<WaveLinkEffect> effects,
        @Nullable WaveLinkImage image) implements WithId, Mergable<WaveLinkChannel> {

    public WaveLinkChannel blank() {
        return new WaveLinkChannel(id);
    }

    private WaveLinkChannel(String id) {
        this(id, null, null, null, null, null, null, null, null);
    }

    public Optional<WaveLinkMix> findMix(String id) {
        return Optional.ofNullable(mixes)
                       .stream().flatMap(List::stream)
                       .filter(mix -> StringUtils.equalsIgnoreCase(mix.id(), id))
                       .findFirst();
    }

    @Override
    public WaveLinkChannel merge(@Nullable WaveLinkChannel other) {
        if (other == null)
            return this;
        return new WaveLinkChannel(
                ObjectUtils.firstNonNull(other.id, id),
                ObjectUtils.firstNonNull(other.name, name),
                ObjectUtils.firstNonNull(other.type, type),
                ObjectUtils.firstNonNull(other.mixes, mixes),
                ObjectUtils.firstNonNull(other.level, level),
                ObjectUtils.firstNonNull(other.isMuted, isMuted),
                ObjectUtils.firstNonNull(other.apps, apps),
                ObjectUtils.firstNonNull(other.effects, effects),
                ObjectUtils.firstNonNull(other.image, image)
        );
    }
}
