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
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Command> dialData = new HashMap<>();
    @JsonDeserialize(using = KnobSettingMapDeserializer.class) private Map<Integer, KnobSetting> knobSettings = new HashMap<>();
    private LightingConfig lightingConfig;
    private boolean focusBackOnLost;
    private List<String> activateApplications = new ArrayList<>();
    private String activationShortcut;
    private Map<Integer, String> oscBindings = new HashMap<>(); // Button/dial to OSC address

    public Profile(String name, DeviceType dt) {
        this.name = name;
        lightingConfig = LightingConfig.defaultLightingConfig(dt);
    }

    protected Profile() {
    }

    public Profile setLightingConfig(LightingConfig lightingConfig) {
        this.lightingConfig = lightingConfig.deepCopy();
        return this;
    }

    public String toString() {
        return name;
    }
}

