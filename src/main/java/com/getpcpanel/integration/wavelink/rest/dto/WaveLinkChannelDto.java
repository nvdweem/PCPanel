package com.getpcpanel.integration.wavelink.rest.dto;

import java.util.List;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkImage;
import jakarta.annotation.Nullable;

public record WaveLinkChannelDto(String id, @Nullable String name, @Nullable String type, @Nullable String image,
                                 List<WaveLinkMixDto> mixes, List<WaveLinkAppDto> apps, List<WaveLinkEffectDto> effects) {
    public static WaveLinkChannelDto from(WaveLinkChannel c) {
        return new WaveLinkChannelDto(
                c.id(),
                c.name(),
                c.type(),
                imageDataUri(c.image()),
                c.mixes().stream().map(WaveLinkMixDto::from).toList(),
                c.apps().stream().map(WaveLinkAppDto::from).toList(),
                c.effects().stream().map(WaveLinkEffectDto::from).toList()
        );
    }

    /** Elgato sends the channel icon as base64 PNG; expose it as a data-URI the UI can drop straight
     *  into an &lt;img&gt; so the device-preview chip shows the same icon the Windows overlay does. */
    @Nullable
    private static String imageDataUri(@Nullable WaveLinkImage image) {
        var data = image == null ? null : image.imgData();
        return data == null || data.isBlank() ? null : "data:image/png;base64," + data;
    }
}
