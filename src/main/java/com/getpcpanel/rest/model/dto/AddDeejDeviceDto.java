package com.getpcpanel.rest.model.dto;

import javax.annotation.Nullable;

/**
 * Request body for manually adding a Deej device: a serial port name, optional baud (default 9600),
 * optional noise-reduction level ({@code low}/{@code default}/{@code high}) and optional display
 * name.
 */
public record AddDeejDeviceDto(
        String port,
        @Nullable Integer baud,
        @Nullable String noiseReduction,
        @Nullable String name
) {
}
