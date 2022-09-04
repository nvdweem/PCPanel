package com.getpcpanel.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.util.Util;

import javafx.scene.paint.Color;
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

    @JsonIgnore
    public void setColorFromColor(Color color) {
        this.color = Util.formatHexString(color);
    }

    @JsonIgnore
    public void setMuteOverrideColorFromColor(Color color) {
        muteOverrideColor = Util.formatHexString(color);
    }

    public void set(SingleSliderLabelLightingConfig c) {
        color = c.color;
        mode = c.mode;
    }
}

