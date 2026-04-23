package com.getpcpanel.rest.model.ws;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.rest.model.dto.ProfileSnapshotDto;

@JsonTypeName("profile_switched")
public record WsProfileSwitchedEvent(
        String serial,
        String profileName,
        ProfileSnapshotDto profileSnapshot,
        List<String> dialColors,
        List<String> sliderLabelColors,
        List<List<String>> sliderColors,
        String logoColor
) implements WsEvent {
}
