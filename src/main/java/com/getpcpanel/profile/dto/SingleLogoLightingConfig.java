package com.getpcpanel.profile.dto;

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
