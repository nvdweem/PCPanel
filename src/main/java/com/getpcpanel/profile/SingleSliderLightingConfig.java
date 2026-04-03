package com.getpcpanel.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.util.Util;

import lombok.Data;

@Data
public class SingleSliderLightingConfig {
    private SINGLE_SLIDER_MODE mode;
    private String color1;
    private String color2;
    private String muteOverrideDeviceOrFollow;
    private String muteOverrideColor;

    public SingleSliderLightingConfig() {
        mode = SINGLE_SLIDER_MODE.NONE;
    }

    public enum SINGLE_SLIDER_MODE {
        NONE, STATIC, STATIC_GRADIENT, VOLUME_GRADIENT
    }

    public void set(SingleSliderLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        mode = c.mode;
    }
}
