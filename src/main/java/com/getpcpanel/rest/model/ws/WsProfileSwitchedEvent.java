package com.getpcpanel.rest.model.ws;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.rest.model.dto.ProfileSnapshotDto;

@JsonTypeName("profile_switched")
public record WsProfileSwitchedEvent(
        String serial,
        String profileName,
        ProfileSnapshotDto profileSnapshot,
        @Nullable ProfileSnapshotDto baseLayerSnapshot,
        LightingConfig lightingConfig,
        List<String> dialColors,
        List<String> sliderLabelColors,
        List<List<String>> sliderColors,
        String logoColor
) implements WsEvent {
}
