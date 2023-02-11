package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.DeviceType;

import lombok.Data;

@Data
public class Profile {
    private String name;
    @JsonProperty("isMainProfile") private boolean isMainProfile;
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> buttonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> dblButtonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> dialData = new HashMap<>();
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

    public Command getButtonData(int button) {
        return buttonData.get(button);
    }

    public Command getDblButtonData(int button) {
        return dblButtonData.get(button);
    }

    public void setButtonData(int button, Command data) {
        buttonData.put(button, data);
    }

    public void setDblButtonData(int button, Command data) {
        dblButtonData.put(button, data);
    }

    public Command getDialData(int dial) {
        return dialData.get(dial);
    }

    public void setDialData(int dial, Command data) {
        dialData.put(dial, data);
    }
}
