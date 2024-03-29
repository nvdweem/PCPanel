package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.device.DeviceType;

import lombok.Data;

@Data
public class Profile {
    private String name;
    @JsonProperty("isMainProfile") private boolean isMainProfile;
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> buttonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> dblButtonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> dialData = new HashMap<>();
    @JsonDeserialize(using = KnobSettingMapDeserializer.class) private Map<Integer, KnobSetting> knobSettings = new HashMap<>();
    private LightingConfig lightingConfig;
    private boolean focusBackOnLost;
    private List<String> activateApplications = new ArrayList<>();
    private String activationShortcut;
    private Map<Integer, OSCBinding> oscBinding = new HashMap<>(); // Button/dial to OSC binding

    public Profile(String name, DeviceType dt) {
        this.name = name;
        lightingConfig = LightingConfig.defaultLightingConfig(dt);
    }

    protected Profile() {
    }

    public LightingConfig getLightingConfig() {
        return lightingConfig.deepCopy();
    }

    public void setLightingConfig(LightingConfig lightingConfig) {
        this.lightingConfig = lightingConfig.deepCopy();
    }

    public String toString() {
        return name;
    }

    public KnobSetting getKnobSettings(int knob) {
        return knobSettings.computeIfAbsent(knob, k -> new KnobSetting());
    }

    public Commands getButtonData(int button) {
        return buttonData.getOrDefault(button, Commands.EMPTY);
    }

    public Commands getDblButtonData(int button) {
        return dblButtonData.get(button);
    }

    public void setButtonData(int button, Commands data) {
        buttonData.put(button, data);
    }

    public void setDblButtonData(int button, Commands data) {
        dblButtonData.put(button, data);
    }

    public Commands getDialData(int dial) {
        return dialData.get(dial);
    }

    public void setDialData(int dial, Commands data) {
        dialData.put(dial, data);
    }
}
