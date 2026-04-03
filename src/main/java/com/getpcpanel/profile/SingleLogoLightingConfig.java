package com.getpcpanel.profile;

import com.getpcpanel.util.Util;

import lombok.Data;

@Data
public class SingleLogoLightingConfig {
    private SINGLE_LOGO_MODE mode;
    private String color;
    private byte brightness;
    private byte speed;
    private byte hue;

    public SingleLogoLightingConfig() {
        mode = SINGLE_LOGO_MODE.NONE;
    }

    public enum SINGLE_LOGO_MODE {
        NONE, STATIC, RAINBOW, BREATH
    }

    /**
     * Used by Jackson
     */
    public SingleLogoLightingConfig setColor(String color) {
        this.color = color;
        return this;
    }
}
