package com.getpcpanel.rest.model.dto;

/**
 * The lighting trace (Settings > Debug).
 *
 * @param enabled whether every report sent to a PCPanel is being recorded
 * @param file    the trace file the records go to
 */
public record LightingTraceStateDto(boolean enabled, String file) {
}
