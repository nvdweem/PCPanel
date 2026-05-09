package com.getpcpanel.rest.model.dto;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.profile.dto.KnobSetting;

import jakarta.annotation.Nullable;

public record ControlAssignmentsUpdateDto(
        @Nullable Commands analog,
        @Nullable Commands button,
        @Nullable Commands dblButton,
        @Nullable KnobSetting knobSetting
) {
}
