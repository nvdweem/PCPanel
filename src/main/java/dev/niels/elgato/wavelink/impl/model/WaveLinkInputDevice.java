package dev.niels.elgato.wavelink.impl.model;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkInputDevice(
        String id,
        @Nullable String name,
        @Nullable Boolean isWaveDevice,
        @Nullable List<Object> inputs
) implements WithId {
    public WaveLinkInputDevice blank() {
        return new WaveLinkInputDevice(id, null, null, null);
    }
}
