package com.getpcpanel.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class SingleLogoLightingConfig {
    private SINGLE_LOGO_MODE mode;
    private String color;
    private byte brightness;
    private byte speed;
    private byte hue;
    /** What an {@code AUDIO_LEVEL} light meters: blank follows the control (or the default output), else an audio-device name or {@code app:<exe>}. */
    @javax.annotation.Nullable private String audioLevelSource;
    /**
     * The brightness (1–100) this light shows at instead of the panel's; null follows the panel. Only a colour override
     * sets it (a notification light with a brightness of its own), so it is never saved.
     */
    @JsonIgnore @javax.annotation.Nullable private Integer overrideBrightness;

    public SingleLogoLightingConfig() {
        mode = SINGLE_LOGO_MODE.NONE;
    }

    public enum SINGLE_LOGO_MODE {
        NONE, STATIC, RAINBOW, BREATH, AUDIO_LEVEL
    }

    /**
     * Used by Jackson
     */
    public SingleLogoLightingConfig setColor(String color) {
        this.color = color;
        return this;
    }
}
