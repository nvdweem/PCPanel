package com.getpcpanel.profile;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.util.Util;

import javafx.scene.paint.Color;
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
        if (color == null) {
            muteOverrideColor = null;
        } else {
            muteOverrideColor = Util.formatHexString(color);
        }
    }

    public void set(SingleKnobLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        muteOverrideColor = c.muteOverrideColor;
        mode = c.mode;
    }
}

