package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.device.DeviceType;

import lombok.Data;
import lombok.NoArgsConstructor;
import one.util.streamex.StreamEx;

@Data
@NoArgsConstructor
public class DeviceSave {
    private String displayName;
    private List<Profile> profiles = new ArrayList<>();
    private String currentProfileName;

    public DeviceSave(Save parent, DeviceType dt) {
        var i = 1;
        while (true) {
            var name = "pcpanel" + i;
            i++;
            if (!parent.doesDeviceDisplayNameExist(name)) {
                displayName = name;
                break;
            }
        }

        ensureCurrentProfile(dt);
    }

    public Optional<Profile> setCurrentProfile(String p) {
        var profile = getProfile(p);
        if (profile.isEmpty())
            return Optional.empty();
        currentProfileName = p;
        return profile;
    }

    public Optional<Profile> getProfile(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return StreamEx.of(getProfiles()).findFirst(p -> p.getName().equals(name));
    }

    @JsonIgnore
    private Optional<Profile> getCurrentProfile() {
        var p = getProfile(currentProfileName);
        if (!profiles.isEmpty() && p.isEmpty()) {
            return Optional.of(getProfiles().get(0));
        }
        return p;
    }

    @SuppressWarnings("unused") // Used by Jackson when deserializing
    public void setCurrentProfileName(String currentProfileName) {
        this.currentProfileName = currentProfileName;
        setCurrentProfile(currentProfileName);
    }

    public Profile ensureCurrentProfile(DeviceType dt) {
        return getCurrentProfile().orElseGet(() -> {
            var profile = new Profile("profile1", dt);
            profiles.add(profile);
            currentProfileName = profile.getName();
            return profile;
        });
    }
}
