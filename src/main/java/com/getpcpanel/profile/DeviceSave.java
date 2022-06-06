package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.DeviceType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeviceSave {
    private String displayName;
    private List<Profile> profiles;
    private String currentProfile;
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> buttonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> dialData = new HashMap<>();
    @JsonDeserialize(using = KnobSettingMapDeserializer.class) private Map<Integer, KnobSetting> knobSettings = new HashMap<>();
    private LightingConfig lightingConfig;

    public DeviceSave(DeviceType dt) {
        lightingConfig = LightingConfig.defaultLightingConfig(dt);
    }

    public DeviceSave generateDefaultDisplayName(Save parent) {
        var i = 1;
        while (true) {
            var name = "pcpanel" + i;
            i++;
            if (!parent.doesDeviceDisplayNameExist(name)) {
                displayName = name;
                return this;
            }
        }
    }

    public KnobSetting getKnobSettings(int knob) {
        return knobSettings.computeIfAbsent(knob, k -> new KnobSetting());
    }

    public Command getButtonData(int button) {
        return buttonData.get(button);
    }

    public void setButtonData(int button, Command data) {
        buttonData.put(button, data);
    }

    public Command getDialData(int dial) {
        return dialData.get(dial);
    }

    public void setDialData(int dial, Command data) {
        dialData.put(dial, data);
    }

    public List<Profile> getProfiles() {
        if (profiles == null || profiles.isEmpty()) {
            if (profiles == null)
                profiles = new ArrayList<>();
            var profile = new Profile();
            profile.setName("profile1");
            profile.setButtonData(buttonData);
            profile.setDialData(dialData);
            profile.setKnobSettings(getKnobSettings());
            profile.setLightingConfig(getLightingConfig());
            profiles.add(profile);
            currentProfile = profile.getName();
        }
        return profiles;
    }

    public boolean setCurrentProfile(String p) {
        var profile = getProfile(p);
        if (profile == null)
            return false;
        currentProfile = p;
        buttonData = profile.getButtonData();
        dialData = profile.getDialData();
        knobSettings = profile.getKnobSettings();
        setLightingConfig(profile.getLightingConfig());
        return true;
    }

    public Profile getProfile(String name) {
        if (name == null)
            return null;
        return getProfiles().stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    public DeviceSave setLightingConfig(LightingConfig lightingConfig) {
        this.lightingConfig = lightingConfig.deepCopy();
        return this;
    }

    @JsonIgnore
    public String getCurrentProfileName() {
        var p = getProfile(currentProfile);
        if (p == null)
            return getProfiles().get(0).getName();
        return currentProfile;
    }

    @JsonIgnore
    public Profile getCurrentProfile() {
        var p = getProfile(currentProfile);
        if (p == null)
            return getProfiles().get(0);
        return p;
    }
}
