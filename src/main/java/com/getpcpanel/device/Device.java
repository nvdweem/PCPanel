package com.getpcpanel.device;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;

import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
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

    public LightingConfig getLightingConfig() {
        if (lightingConfig == null) {
            lightingConfig = currentProfile().getLightingConfig();
        }
        return lightingConfig;
    }

    public LightingConfig getSavedLightingConfig() {
        return currentProfile().getLightingConfig();
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
            config = LightingConfig.defaultLightingConfig(getDeviceType());
            saveService.save();
        }
        try {
            outputInterpreter.sendLightingConfig(getSerialNumber(), getDeviceType(), config, priority);
        } catch (Exception e) {
            log.error("Unable to send lighting config", e);
            setLighting(LightingConfig.defaultLightingConfig(getDeviceType()), priority);
        }
    }

    public void focusApplicationChanged() {
    }

    public void saveChanged() {
    }

    public abstract DeviceType getDeviceType();

    public abstract void setKnobRotation(int paramInt1, int paramInt2);

    public abstract int getKnobRotation(int knob);

    public abstract void setButtonPressed(int paramInt, boolean paramBoolean);

    public abstract void closeDialogs();

    public abstract void showLightingConfigToUI(LightingConfig paramLightingConfig);

    public void disconnected() {
        closeDialogs();
    }

    public Profile currentProfile() {
        return save.ensureCurrentProfile(getDeviceType());
    }
}
