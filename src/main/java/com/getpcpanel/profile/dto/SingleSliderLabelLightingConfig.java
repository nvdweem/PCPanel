package com.getpcpanel.profile.dto;

import lombok.Data;

@Data
public class SingleSliderLabelLightingConfig {
    private SINGLE_SLIDER_LABEL_MODE mode;

    private String color;
    private String muteOverrideDeviceOrFollow;
    private String muteOverrideColor;

    public SingleSliderLabelLightingConfig() {
        mode = SINGLE_SLIDER_LABEL_MODE.NONE;
    }

    public enum SINGLE_SLIDER_LABEL_MODE {
        NONE, STATIC
    }

    public void set(SingleSliderLabelLightingConfig c) {
        color = c.color;
        mode = c.mode;
    }
}
