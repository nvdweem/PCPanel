package save;

import javafx.scene.paint.Color;
import util.Util;

public class SingleSliderLabelLightingConfig {
    private SINGLE_SLIDER_LABEL_MODE mode;

    private String color;

    public SingleSliderLabelLightingConfig() {
        mode = SINGLE_SLIDER_LABEL_MODE.NONE;
    }

    public enum SINGLE_SLIDER_LABEL_MODE {
        NONE, STATIC
    }

    public SINGLE_SLIDER_LABEL_MODE getMode() {
        return mode;
    }

    public void setMode(SINGLE_SLIDER_LABEL_MODE mode) {
        this.mode = mode;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setColor(Color color) {
        this.color = Util.formatHexString(color);
    }

    public void set(SingleSliderLabelLightingConfig c) {
        color = c.color;
        mode = c.mode;
    }
}

