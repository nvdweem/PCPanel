package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.List;

import com.getpcpanel.device.DeviceType;

public class DeviceSave {
    private String displayName;
    private List<Profile> profiles;
    private String currentProfile;
    public String[][] buttonData;
    public String[][] dialData;
    private KnobSetting[] knobSettings;
    private LightingConfig lightingConfig;

    public DeviceSave(DeviceType dt) {
        var analogCount = dt.getAnalogCount();
        var buttonCount = dt.getButtonCount();
        buttonData = new String[buttonCount][10];
        dialData = new String[analogCount][10];
        displayName = generateDefaultDisplayName();
        lightingConfig = LightingConfig.defaultLightingConfig(dt);
        knobSettings = new KnobSetting[analogCount];
        for (var i = 0; i < analogCount; i++) {
            knobSettings[i] = new KnobSetting();
        }
    }

    private static String generateDefaultDisplayName() {
        var i = 1;
        while (true) {
            var name = "pcpanel" + i;
            i++;
            if (!Save.doesDeviceDisplayNameExist(name))
                return name;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LightingConfig getLightingConfig() {
        return lightingConfig;
    }

    public void setLightingConfig(LightingConfig lightingConfig) {
        this.lightingConfig = lightingConfig;
    }

    public KnobSetting[] getKnobSettings() {
        if (knobSettings == null) {
            knobSettings = new KnobSetting[buttonData.length];
            for (var i = 0; i < buttonData.length; i++) {
                knobSettings[i] = new KnobSetting();
            }
        }
        return knobSettings;
    }

    public List<Profile> getProfiles() {
        if (profiles == null || profiles.isEmpty()) {
            if (profiles == null)
                profiles = new ArrayList<>();
            var profile = new Profile();
            profile.name = "profile1";
            profile.buttonData = buttonData;
            profile.dialData = dialData;
            profile.knobSettings = getKnobSettings();
            profile.lightingConfig = getLightingConfig();
            profiles.add(profile);
            currentProfile = profile.name;
        }
        return profiles;
    }

    public boolean setCurrentProfile(String p) {
        var profile = getProfile(p);
        if (profile == null)
            return false;
        currentProfile = p;
        buttonData = profile.buttonData;
        dialData = profile.dialData;
        knobSettings = profile.knobSettings;
        lightingConfig = profile.lightingConfig;
        return true;
    }

    public Profile getProfile(String name) {
        if (name == null)
            return null;
        for (var p : getProfiles()) {
            if (p.name.equals(name))
                return p;
        }
        return null;
    }

    public String getCurrentProfileName() {
        var p = getProfile(currentProfile);
        if (p == null)
            return getProfiles().get(0).getName();
        return currentProfile;
    }

    public Profile getCurrentProfile() {
        var p = getProfile(currentProfile);
        if (p == null)
            return getProfiles().get(0);
        return p;
    }
}

