package com.getpcpanel.device;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingChangedToDefaultEvent;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.ProfileSwitchedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;

import jakarta.enterprise.event.Event;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public abstract class Device {
    private final SaveService saveService;
    private final OutputInterpreter outputInterpreter;
    private final IconService iconService;
    private final Event<Object> eventBus;
    @Getter protected String serialNumber;
    protected DeviceSave save;
    private LightingConfig lightingConfig;

    protected Device(SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, Event<Object> eventBus, String serialNum, DeviceSave deviceSave) {
        this.saveService = saveService;
        this.outputInterpreter = outputInterpreter;
        this.iconService = iconService;
        this.eventBus = eventBus;
        serialNumber = serialNum;
        save = deviceSave;
    }

    protected void postInit() {
    }

    public void focusChanged(String from, String to) {
        if (!StringUtils.equals(from, to) && switchForApplication(to))
            return;
        switchAwayFromApplication(from);
    }

    private boolean switchForApplication(String to) {
        var target = StreamEx.of(save.getProfiles())
                             .findFirst(p -> StreamEx.of(p.getActivateApplications()).anyMatch(a -> StringUtils.equalsIgnoreCase(a, to)));
        target.filter(p -> p != currentProfile()).ifPresent(p -> switchProfile(p.getName()));
        return target.isPresent();
    }

    private void switchAwayFromApplication(String from) {
        var current = currentProfile();
        if (!current.isFocusBackOnLost() || StreamEx.of(current.getActivateApplications()).noneMatch(a -> StringUtils.equalsIgnoreCase(a, from))) {
            return;
        }
        StreamEx.of(save.getProfiles())
                .findFirst(Profile::isMainProfile)
                .filter(p -> p != current)
                .ifPresent(p -> switchProfile(p.getName()));
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

    public void disconnected() {
    }

    public void switchProfile(@Nullable String profileName) {
        save.setCurrentProfile(profileName).ifPresent(profile -> {
            setLighting(profile.lightingConfig(), true);
            saveService.save();
            eventBus.fire(new LightingChangedToDefaultEvent(serialNumber));
            eventBus.fire(new ProfileSwitchedEvent(serialNumber, profile.getName()));
        });
    }

    public Profile currentProfile() {
        return save.ensureCurrentProfile(deviceType());
    }
}
