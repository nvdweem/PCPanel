package save;

import main.DeviceType;

public class Profile {
    protected String name;
    public String[][] buttonData;
    public String[][] dialData;
    protected KnobSetting[] knobSettings;
    protected LightingConfig lightingConfig;

    public Profile(String name, DeviceType dt) {
        this.name = name;
        buttonData = new String[dt.getButtonCount()][10];
        dialData = new String[dt.getAnalogCount()][10];
        lightingConfig = LightingConfig.defaultLightingConfig(dt);
        knobSettings = new KnobSetting[dt.getAnalogCount()];
        for (var i = 0; i < dt.getAnalogCount(); i++) {
            knobSettings[i] = new KnobSetting();
        }
    }

    protected Profile() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}

