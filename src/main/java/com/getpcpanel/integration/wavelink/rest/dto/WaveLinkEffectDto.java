package com.getpcpanel.integration.wavelink.rest.dto;

import dev.niels.wavelink.impl.model.WaveLinkEffect;
import jakarta.annotation.Nullable;

public record WaveLinkEffectDto(String id, @Nullable String name, boolean isEnabled) {
    public static WaveLinkEffectDto from(WaveLinkEffect waveLinkEffect) {
        return new WaveLinkEffectDto(
                waveLinkEffect.id(),
                waveLinkEffect.name(),
                Boolean.TRUE.equals(waveLinkEffect.isEnabled())
        );
    }
}
