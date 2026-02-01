package dev.niels.wavelink.impl.model;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record WaveLinkInputDevice(
        String id,
        @Nullable String name,
        @Nullable Boolean isWaveDevice,
        @Nullable List<Object> inputs
) implements WithId, Mergable<WaveLinkInputDevice> {
    @Override
    public WaveLinkInputDevice merge(@Nullable WaveLinkInputDevice other) {
        if (other == null)
            return this;
        return new WaveLinkInputDevice(
                ObjectUtils.firstNonNull(other.id, id),
                ObjectUtils.firstNonNull(other.name, name),
                ObjectUtils.firstNonNull(other.isWaveDevice, isWaveDevice),
                ObjectUtils.firstNonNull(other.inputs, inputs)
        );
    }
}
