package com.getpcpanel.integration.wavelink.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param enabled                 Wave Link integration on/off.
 * @param focusVolumeRedirect     when a Wave-Link-controlled app has focus, route the focused-app
 *                                volume to Wave Link instead of the OS (the focus-control feature).
 * @param enforceControlledVolume pin the OS volume of a focused Wave-Link-controlled app to
 *                                {@code controlledVolumePercent} (Wave Link does the real control).
 * @param controlledVolumePercent the OS volume (0-100) to pin controlled apps to; default 100.
 */
public record WaveLinkSettings(boolean enabled, boolean focusVolumeRedirect, boolean enforceControlledVolume,
        int controlledVolumePercent) {
    public static final WaveLinkSettings DEFAULT = new WaveLinkSettings(false, true, false, 100);

    /**
     * Defaults missing fields so older saves (which only had {@code enabled}) keep the focus-control
     * feature on and a sensible 100% target rather than getting {@code false}/{@code 0}.
     */
    @JsonCreator
    public WaveLinkSettings(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("focusVolumeRedirect") @Nullable Boolean focusVolumeRedirect,
            @JsonProperty("enforceControlledVolume") boolean enforceControlledVolume,
            @JsonProperty("controlledVolumePercent") @Nullable Integer controlledVolumePercent) {
        this(enabled,
                focusVolumeRedirect == null || focusVolumeRedirect,
                enforceControlledVolume,
                controlledVolumePercent == null ? 100 : controlledVolumePercent);
    }
}
