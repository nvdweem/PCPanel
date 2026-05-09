package com.getpcpanel.rest.model.ws;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.profile.dto.LightingConfig;

@JsonTypeName("lighting_changed")
public record WsLightingChangedEvent(
        String serial,
        LightingConfig lightingConfig,
        List<String> dialColors,
        List<String> sliderLabelColors,
        List<List<String>> sliderColors,
        String logoColor
) implements WsEvent {
}
