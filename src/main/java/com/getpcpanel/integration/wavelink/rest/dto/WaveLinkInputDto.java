package com.getpcpanel.integration.wavelink.rest.dto;

import javax.annotation.Nullable;

import dev.niels.wavelink.impl.model.WaveLinkInputDevice;

public record WaveLinkInputDto(String id, @Nullable String name) {
    public static WaveLinkInputDto from(WaveLinkInputDevice waveLinkInput) {
        return new WaveLinkInputDto(
                waveLinkInput.id(),
                waveLinkInput.name()
        );
    }
}
