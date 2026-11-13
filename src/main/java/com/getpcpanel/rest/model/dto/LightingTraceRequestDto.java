package com.getpcpanel.rest.model.dto;

/** Body of {@code PUT /api/lighting-trace}: whether the lighting trace should record. */
public record LightingTraceRequestDto(boolean enabled) {
}
