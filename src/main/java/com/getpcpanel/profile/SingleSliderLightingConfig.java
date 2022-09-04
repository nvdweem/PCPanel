package com.getpcpanel.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.util.Util;

import javafx.scene.paint.Color;
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

    @JsonIgnore
    public void setColor1FromColor(Color color1) {
        this.color1 = Util.formatHexString(color1);
    }

    @JsonIgnore
    public void setColor2FromColor(Color color2) {
        this.color2 = Util.formatHexString(color2);
    }

    @JsonIgnore
    public void setMuteOverrideColorFromColor(Color color) {
        muteOverrideColor = Util.formatHexString(color);
    }

    public void set(SingleSliderLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        mode = c.mode;
    }
}

