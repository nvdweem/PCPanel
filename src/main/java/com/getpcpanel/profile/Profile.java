package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.OSCBinding;

import lombok.Data;

@Data
public class Profile {
    private String name;
    @JsonProperty("isMainProfile") private boolean isMainProfile;
    /**
     * Marks this profile as the device's "base layer": a fallback consulted for any control the active
     * profile leaves unconfigured (no command) or unlit (per-control lighting NONE), mute-colours
     * included. At most one profile per device should carry this flag.
     */
    @JsonProperty("isBaseLayer") private boolean isBaseLayer;
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> buttonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> dblButtonData = new HashMap<>();
    @JsonDeserialize(using = CommandMapDeserializer.class) private Map<Integer, Commands> dialData = new HashMap<>();
    @JsonDeserialize(using = KnobSettingMapDeserializer.class) private Map<Integer, KnobSetting> knobSettings = new HashMap<>();
    private LightingConfig lightingConfig;
    private boolean focusBackOnLost;
    private List<String> activateApplications = new ArrayList<>();
    private String activationShortcut;
    private Map<Integer, OSCBinding> oscBinding = new HashMap<>(); // Button/dial to OSC binding

    /**
     * Builds a profile with its initial lighting from a supplier, decoupled from {@link DeviceType}.
     */
    public Profile(String name, Supplier<LightingConfig> defaultLighting) {
        this.name = name;
        lightingConfig = defaultLighting.get();
    }

    /** @deprecated use {@link #Profile(String, Supplier)}; kept as a shim during the device-layer transition. */
    @Deprecated
    public Profile(String name, DeviceType dt) {
        this(name, () -> LightingConfig.defaultLightingConfig(dt));
    }

    protected Profile() {
    }

    public LightingConfig lightingConfig() {
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
