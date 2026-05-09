package com.getpcpanel.rest.wavelink.dto;

import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import jakarta.annotation.Nullable;

public record WaveLinkOutputDto(String id, @Nullable String name) {
    public static WaveLinkOutputDto from(WaveLinkOutputDevice waveLinkOutput) {
        return new WaveLinkOutputDto(
                waveLinkOutput.id(),
                waveLinkOutput.name()
        );
    }
}
