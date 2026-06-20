package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.dto.LightingConfig;

import lombok.Data;
import lombok.NoArgsConstructor;
import one.util.streamex.StreamEx;

@Data
@NoArgsConstructor
public class DeviceSave {
    private String displayName;
    private List<Profile> profiles = new ArrayList<>();
    private String currentProfileName;

    /**
     * Self-identifying persistence (Phase 2): the provider that owns this device, the provider's
     * device-kind id, and a snapshot of its capability descriptor. All nullable and default-absent
     * so legacy {@code profiles.json} entries (which carried none of these) deserialize unchanged;
     * back-filled at connect time from the live descriptor. {@code providerId} is migrated to
     * {@code "pcpanel"} on load for legacy files (it is the only provider they could have been).
     */
    @Nullable private String providerId;
    @Nullable private String deviceKindId;
    @Nullable private DeviceDescriptor capabilities;

    /**
     * Builds a fresh save for a device described by {@code descriptor}. The display-name base is
     * derived from the provider so a PCPanel device still becomes {@code pcpanel1}, {@code pcpanel2},
     * ... exactly as before.
     */
    public DeviceSave(Save parent, DeviceDescriptor descriptor) {
        this(parent, descriptor.providerId(), () -> LightingConfig.defaultLightingConfig(descriptor));
        providerId = descriptor.providerId();
        deviceKindId = descriptor.deviceKindId();
        capabilities = descriptor;
    }

    /**
     * Builds a fresh save with an explicit display-name base and a default-lighting supplier,
     * decoupled from {@link DeviceType}.
     */
    public DeviceSave(Save parent, String displayNameBase, Supplier<LightingConfig> defaultLighting) {
        var i = 1;
        while (true) {
            var name = displayNameBase + i;
            i++;
            if (!parent.doesDeviceDisplayNameExist(name)) {
                displayName = name;
                break;
            }
        }

        ensureCurrentProfile(defaultLighting);
    }

    /** @deprecated use {@link #DeviceSave(Save, DeviceDescriptor)}; kept as a shim during the device-layer transition. */
    @Deprecated
    public DeviceSave(Save parent, DeviceType dt) {
        this(parent, "pcpanel", () -> LightingConfig.defaultLightingConfig(dt));
    }

    public Optional<Profile> setCurrentProfile(String p) {
        var profile = getProfile(p);
        if (profile.isEmpty())
            return Optional.empty();
        currentProfileName = p;
        return profile;
    }

    public Optional<Profile> getProfile(@Nullable String name) {
        if (name == null) {
            return Optional.empty();
        }
        return StreamEx.of(profiles).findFirst(p -> p.getName().equals(name));
    }

    @JsonIgnore
    private Optional<Profile> getCurrentProfile() {
        var p = getProfile(currentProfileName);
        if (!profiles.isEmpty() && p.isEmpty()) {
            return Optional.of(profiles.get(0));
        }
        return p;
    }

    @SuppressWarnings("unused") // Used by Jackson when deserializing
    public void setCurrentProfileName(String currentProfileName) {
        this.currentProfileName = currentProfileName;
        setCurrentProfile(currentProfileName);
    }

    public Profile ensureCurrentProfile(Supplier<LightingConfig> defaultLighting) {
        return getCurrentProfile().orElseGet(() -> {
            var profile = new Profile("profile1", defaultLighting);
            profiles.add(profile);
            currentProfileName = profile.getName();
            return profile;
        });
    }

    /** @deprecated use {@link #ensureCurrentProfile(Supplier)}; kept as a shim during the device-layer transition. */
    @Deprecated
    public Profile ensureCurrentProfile(DeviceType dt) {
        return ensureCurrentProfile(() -> LightingConfig.defaultLightingConfig(dt));
    }
}
