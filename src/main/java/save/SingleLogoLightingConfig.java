package save;

import javafx.scene.paint.Color;
import util.Util;

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

    public SINGLE_LOGO_MODE getMode() {
        return mode;
    }

    public void setMode(SINGLE_LOGO_MODE mode) {
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

    public byte getBrightness() {
        return brightness;
    }

    public void setBrightness(byte brightness) {
        this.brightness = brightness;
    }

    public byte getSpeed() {
        return speed;
    }

    public void setSpeed(byte speed) {
        this.speed = speed;
    }

    public byte getHue() {
        return hue;
    }

    public void setHue(byte hue) {
        this.hue = hue;
    }
}

