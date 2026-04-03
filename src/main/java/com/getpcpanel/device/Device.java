package com.getpcpanel.device;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class Device {
    private final SaveService saveService;
    private final OutputInterpreter outputInterpreter;
    private final IconService iconService;
    @Getter protected String serialNumber;
    protected DeviceSave save;
    private LightingConfig lightingConfig;

    protected Device(SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, String serialNum, DeviceSave deviceSave) {
        this.saveService = saveService;
        this.outputInterpreter = outputInterpreter;
        this.iconService = iconService;
        serialNumber = serialNum;
        save = deviceSave;
    }

    protected void postInit() {
    }

    public void focusChanged(String from, String to) {
    }

    public String getDisplayName() {
        return save.getDisplayName();
    }

    public void setDisplayName(String name) {
        save.setDisplayName(name);
    }

    public LightingConfig lightingConfig() {
        if (lightingConfig == null) {
            lightingConfig = currentProfile().lightingConfig();
        }
        return lightingConfig;
    }

    public LightingConfig getSavedLightingConfig() {
        return currentProfile().lightingConfig();
    }

    public void setLighting(LightingConfig config, boolean priority) {
        doSetLighting(config, priority);
    }

    public void setSavedLighting(LightingConfig config) {
        currentProfile().setLightingConfig(config);
        doSetLighting(config, true);
    }

    private void doSetLighting(LightingConfig config, boolean priority) {
        lightingConfig = config;
        if (config == null) {
            config = LightingConfig.defaultLightingConfig(deviceType());
            saveService.save();
        }
        try {
            outputInterpreter.sendLightingConfig(serialNumber, deviceType(), config, priority);
        } catch (Exception e) {
            log.error("Unable to send lighting config", e);
            setLighting(LightingConfig.defaultLightingConfig(deviceType()), priority);
        }
    }

    public void focusApplicationChanged() {
    }

    public void saveChanged() {
    }

    public abstract DeviceType deviceType();

    public abstract void setKnobRotation(int paramInt1, int paramInt2);

    public abstract int getKnobRotation(int knob);

    public abstract void setButtonPressed(int paramInt, boolean paramBoolean);

    public abstract void closeDialogs();

    public abstract void showLightingConfigToUI(LightingConfig paramLightingConfig);

    public void disconnected() {
        closeDialogs();
    }

    public void setProfile(String profileName) {
        save.setCurrentProfileName(profileName);
    }

    public Profile currentProfile() {
        return save.ensureCurrentProfile(deviceType());
    }
}
