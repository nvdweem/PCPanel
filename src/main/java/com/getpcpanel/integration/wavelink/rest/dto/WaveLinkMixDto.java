package com.getpcpanel.integration.wavelink.rest.dto;

import dev.niels.wavelink.impl.model.WaveLinkMix;
import jakarta.annotation.Nullable;

public record WaveLinkMixDto(String id, @Nullable String name) {
    public static WaveLinkMixDto from(WaveLinkMix waveLinkMix) {
        return new WaveLinkMixDto(
                waveLinkMix.id(),
                waveLinkMix.name()
        );
    }
}
