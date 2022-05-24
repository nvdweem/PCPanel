package save;

import javafx.scene.paint.Color;
import util.Util;

public class SingleSliderLightingConfig {
    private SINGLE_SLIDER_MODE mode;
    private String color1;
    private String color2;

    public SingleSliderLightingConfig() {
        mode = SINGLE_SLIDER_MODE.NONE;
    }

    public enum SINGLE_SLIDER_MODE {
        NONE, STATIC, STATIC_GRADIENT, VOLUME_GRADIENT
    }

    public SINGLE_SLIDER_MODE getMode() {
        return mode;
    }

    public void setMode(SINGLE_SLIDER_MODE mode) {
        this.mode = mode;
    }

    public String getColor1() {
        return color1;
    }

    public void setColor1(String color1) {
        this.color1 = color1;
    }

    public void setColor1(Color color1) {
        this.color1 = Util.formatHexString(color1);
    }

    public String getColor2() {
        return color2;
    }

    public void setColor2(String color2) {
        this.color2 = color2;
    }

    public void setColor2(Color color2) {
        this.color2 = Util.formatHexString(color2);
    }

    public void set(SingleSliderLightingConfig c) {
        color1 = c.color1;
        color2 = c.color2;
        mode = c.mode;
    }
}

