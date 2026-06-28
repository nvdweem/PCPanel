package com.getpcpanel.integration.wavelink.rest.dto;

import dev.niels.wavelink.impl.model.WaveLinkApp;

public record WaveLinkAppDto(String id, String name) {
    public static WaveLinkAppDto from(WaveLinkApp waveLinkApp) {
        return new WaveLinkAppDto(
                waveLinkApp.id(),
                waveLinkApp.name()
        );
    }
}
