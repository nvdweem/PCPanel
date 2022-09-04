package com.getpcpanel.profile;

import com.getpcpanel.util.Util;

import javafx.scene.paint.Color;
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

    public SingleLogoLightingConfig setColor(Color color) {
        this.color = Util.formatHexString(color);
        return this;
    }

    /**
     * Used by Jackson
     */
    public void setColor(String color) {
        this.color = color;
    }
}

