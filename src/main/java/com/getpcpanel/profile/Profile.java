package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.List;

import com.getpcpanel.device.DeviceType;

import lombok.Data;

@Data
public class Profile {
    private String name;
    private boolean isMainProfile;
    private String[][] buttonData;
    private String[][] dialData;
    private KnobSetting[] knobSettings;
    private LightingConfig lightingConfig;
    private boolean focusBackOnLost;
    private List<String> activateApplications = new ArrayList<>();

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

    public String toString() {
        return name;
    }
}

