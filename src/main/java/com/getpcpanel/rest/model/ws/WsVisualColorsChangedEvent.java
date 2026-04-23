package com.getpcpanel.rest.model.ws;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("visual_colors_changed")
public record WsVisualColorsChangedEvent(
        String serial,
        List<String> dialColors,
        List<String> sliderLabelColors,
        List<List<String>> sliderColors,
        String logoColor
) implements WsEvent {
}
