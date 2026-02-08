package dev.niels.wavelink.impl.model;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkOutputDevice(
        String id,
        @Nullable String name,
        @Nullable Boolean isWaveDevice,
        @Nullable List<WaveLinkOutput> outputs
) implements WithId {
    public WaveLinkOutputDevice blank() {
        return new WaveLinkOutputDevice(id, null, null, null);
    }

    public WaveLinkOutputDevice blankWithOutputs() {
        return new WaveLinkOutputDevice(id, null, null, outputs);
    }

    public WaveLinkOutputDevice withOutputs(List<WaveLinkOutput> outputs) {
        return new WaveLinkOutputDevice(id, name, isWaveDevice, outputs);
    }

    public WaveLinkOutputDevice withOutputs(Function<WaveLinkOutput, WaveLinkOutput> mapper) {
        if (outputs == null)
            return this;

        return new WaveLinkOutputDevice(id, name, isWaveDevice, outputs.stream().map(mapper).toList());
    }
}
