package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class SingleKnobLightingConfig {
    private SINGLE_KNOB_MODE mode;
    private String color1;
    private String color2;
    @Nullable private String muteOverrideDeviceOrFollow;
    @Nullable private String muteOverrideColor;
    /** What an {@code AUDIO_LEVEL} light meters: blank follows the control (or the default output), else an audio-device name or {@code app:<exe>}. */
    @Nullable private String audioLevelSource;
    /**
     * The brightness (1–100) this light shows at instead of the panel's; null follows the panel. Only a colour override
     * sets it (a notification light with a brightness of its own), so it is never saved.
     */
    @JsonIgnore @Nullable private Integer overrideBrightness;

    public SingleKnobLightingConfig() {
        mode = SINGLE_KNOB_MODE.NONE;
    }

    public enum SINGLE_KNOB_MODE {
        NONE, STATIC, VOLUME_GRADIENT, AUDIO_LEVEL
    }

    public void set(SingleKnobLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        muteOverrideColor = c.muteOverrideColor;
        muteOverrideDeviceOrFollow = c.muteOverrideDeviceOrFollow;
        audioLevelSource = c.audioLevelSource;
        overrideBrightness = c.overrideBrightness;
        mode = c.mode;
    }
}
