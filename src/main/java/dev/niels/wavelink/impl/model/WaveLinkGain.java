package dev.niels.wavelink.impl.model;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.With;

@With
@JsonInclude(Include.NON_NULL)
public record WaveLinkGain(
        @Nullable Double value,
        @Nullable Double min,
        @Nullable Double max,
        @Nullable List<Object> lookUpTable
) {
    /** A gain carrying only a target value — what a setInputDevice request sends (min/max/curve are server-owned). */
    public static WaveLinkGain of(double value) {
        return new WaveLinkGain(value, null, null, null);
    }
}
