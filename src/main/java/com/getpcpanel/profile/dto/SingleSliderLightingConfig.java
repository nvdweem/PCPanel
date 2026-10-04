package com.getpcpanel.profile.dto;

import lombok.Data;

@Data
public class SingleSliderLightingConfig {
    private SINGLE_SLIDER_MODE mode;
    private String color1;
    private String color2;
    private String muteOverrideDeviceOrFollow;
    private String muteOverrideColor;
    /** What an {@code AUDIO_LEVEL} light meters: blank follows the control (or the default output), else an audio-device name or {@code app:<exe>}. */
    @javax.annotation.Nullable private String audioLevelSource;

    public SingleSliderLightingConfig() {
        mode = SINGLE_SLIDER_MODE.NONE;
    }

    public enum SINGLE_SLIDER_MODE {
        NONE, STATIC, STATIC_GRADIENT, VOLUME_GRADIENT, AUDIO_LEVEL
    }

    public void set(SingleSliderLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        muteOverrideColor = c.muteOverrideColor;
        muteOverrideDeviceOrFollow = c.muteOverrideDeviceOrFollow;
        audioLevelSource = c.audioLevelSource;
        mode = c.mode;
    }
}
