package dev.niels.wavelink.impl.model;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkInputDevice(
        String id,
        @Nullable String name,
        @Nullable String deviceType,
        @Nullable List<WaveLinkInput> inputs
) implements WithId {
    public WaveLinkInputDevice blank() {
        return new WaveLinkInputDevice(id, null, null, null);
    }

    public WaveLinkInputDevice blankWithInputs() {
        return new WaveLinkInputDevice(id, null, null, inputs);
    }

    public WaveLinkInputDevice withInputs(List<WaveLinkInput> inputs) {
        return new WaveLinkInputDevice(id, name, deviceType, inputs);
    }

    public WaveLinkInputDevice withInputs(Function<WaveLinkInput, WaveLinkInput> mapper) {
        if (inputs == null) {
            return this;
        }
        return new WaveLinkInputDevice(id, name, deviceType, inputs.stream().map(mapper).toList());
    }
}
