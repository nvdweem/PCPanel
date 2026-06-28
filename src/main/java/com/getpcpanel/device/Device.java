package com.getpcpanel.device;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
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
    private final DeviceDescriptor descriptor;
    protected DeviceSave save;
    private LightingConfig lightingConfig;

    protected Device(SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, Event<Object> eventBus, String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        this.saveService = saveService;
        this.outputInterpreter = outputInterpreter;
        this.iconService = iconService;
        this.eventBus = eventBus;
        serialNumber = serialNum;
        save = deviceSave;
        this.descriptor = descriptor;
    }

    public DeviceDescriptor descriptor() {
        return descriptor;
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

    /** Default lighting for this device, resolved from the descriptor (no {@link DeviceType} needed). */
    private LightingConfig defaultLighting() {
        return LightingConfig.defaultLightingConfig(descriptor);
    }

    private void doSetLighting(LightingConfig config, boolean priority) {
        lightingConfig = config;
        // Lightless devices (e.g. Deej) carry no global lighting; never touch the HID output path.
        if (descriptor.globalLighting() == null) {
            return;
        }
        if (config == null) {
            config = defaultLighting();
            saveService.save();
        }
        try {
            outputInterpreter.sendLightingConfig(serialNumber, deviceType(), config, priority);
        } catch (Exception e) {
            log.error("Unable to send lighting config", e);
            setLighting(defaultLighting(), priority);
        }
    }

    public void focusApplicationChanged() {
    }

    public void saveChanged() {
    }

    /**
     * The legacy PCPanel hardware-model enum, or {@code null} for descriptor-only devices (Deej,
     * future generic/MIDI devices) that have no {@link DeviceType}. PCPanel subclasses override it;
     * the rest of {@link Device} no longer requires it (profile/lighting defaults flow through the
     * {@link DeviceDescriptor}).
     */
    @Nullable
    public DeviceType deviceType() {
        return null;
    }

    // Analog indices that have reported at least one real value. getKnobRotation defaults to 0 before the
    // first read, which is indistinguishable from a genuine 0 — so callers that must not act on that
    // not-yet-known 0 (e.g. a brightness dial driving global brightness at connect time) check this first.
    private final java.util.Set<Integer> knobsSeen = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public abstract void setKnobRotation(int paramInt1, int paramInt2);

    public abstract int getKnobRotation(int knob);

    /** Whether {@code knob} has reported a real value yet (vs. the default-0 it reads before any input). */
    public boolean hasKnobRotation(int knob) {
        return knobsSeen.contains(knob);
    }

    /** Subclasses call this from setKnobRotation once a real value has been stored for {@code knob}. */
    protected void markKnobSeen(int knob) {
        knobsSeen.add(knob);
    }

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
        return save.ensureCurrentProfile(this::defaultLighting);
    }
}
