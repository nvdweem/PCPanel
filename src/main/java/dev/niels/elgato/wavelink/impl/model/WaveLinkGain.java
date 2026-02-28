package dev.niels.elgato.wavelink.impl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record WaveLinkGain(
        Double value,
        Double min,
        Double max,
        List<Object> lookUpTable // ?
) {
}
