package dev.niels.wavelink.impl.model;

import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.reactivex.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public record WaveLinkOutputDevice(
        String id,
        @Nullable String name,
        @Nullable Boolean isWaveDevice,
        @Nullable List<WaveLinkOutput> outputs
) implements WithId, Mergable<WaveLinkOutputDevice> {
    @Override
    public WaveLinkOutputDevice merge(@Nullable WaveLinkOutputDevice other) {
        if (other == null)
            return this;
        return new WaveLinkOutputDevice(
                ObjectUtils.firstNonNull(other.id, id),
                ObjectUtils.firstNonNull(other.name, name),
                ObjectUtils.firstNonNull(other.isWaveDevice, isWaveDevice),
                ObjectUtils.firstNonNull(other.outputs, outputs)
        );
    }
}
