package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

import lombok.Data;

@Data
public class SingleKnobLightingConfig {
    private SINGLE_KNOB_MODE mode;
    private String color1;
    private String color2;
    @Nullable private String muteOverrideDeviceOrFollow;
    @Nullable private String muteOverrideColor;

    public SingleKnobLightingConfig() {
        mode = SINGLE_KNOB_MODE.NONE;
    }

    public enum SINGLE_KNOB_MODE {
        NONE, STATIC, VOLUME_GRADIENT
    }

    public void set(SingleKnobLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        muteOverrideColor = c.muteOverrideColor;
        mode = c.mode;
    }
}
