package com.getpcpanel.rest.model.dto;

/** Body of {@code PUT /api/platform/autostart}: the wanted state of the start-with-Windows registration. */
public record AutostartRequestDto(boolean enabled) {
}
