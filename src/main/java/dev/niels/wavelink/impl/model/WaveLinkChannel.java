package dev.niels.wavelink.impl.model;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

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
        List<WaveLinkMix> mixes,
        @Nullable Double level,
        @Nullable Boolean isMuted,
        List<WaveLinkApp> apps,
        List<WaveLinkEffect> effects,
        @Nullable WaveLinkImage image) implements WithId {

    public WaveLinkChannel {
        if (mixes == null) {
            mixes = List.of();
        }
        if (apps == null) {
            apps = List.of();
        }
        if (effects == null) {
            effects = List.of();
        }
    }

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
}
