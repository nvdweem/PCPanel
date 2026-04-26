package com.getpcpanel.rest.wavelink.dto;

import java.util.List;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import jakarta.annotation.Nullable;

public record WaveLinkChannelDto(String id, @Nullable String name, @Nullable String type, List<WaveLinkMixDto> mixes, List<WaveLinkAppDto> apps, List<WaveLinkEffectDto> effects) {
    public static WaveLinkChannelDto from(WaveLinkChannel c) {
        return new WaveLinkChannelDto(
                c.id(),
                c.name(),
                c.type(),
                c.mixes().stream().map(WaveLinkMixDto::from).toList(),
                c.apps().stream().map(WaveLinkAppDto::from).toList(),
                c.effects().stream().map(WaveLinkEffectDto::from).toList()
        );
    }
}
