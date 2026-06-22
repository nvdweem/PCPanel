package com.getpcpanel.rest.model.dto;

import java.util.ArrayList;
import java.util.List;

import com.getpcpanel.profile.Profile;

/**
 * Per-profile activation settings: which applications auto-activate the profile when focused,
 * whether to fall back to the main profile when that app loses focus, the main-profile flag, and the
 * base-layer flag (this profile is the fallback for controls others leave unconfigured/unlit).
 * Backs {@code GET/PUT /api/devices/{serial}/profiles/{name}/settings}.
 */
public record ProfileSettingsDto(String name, boolean isMainProfile, boolean isBaseLayer, boolean focusBackOnLost, List<String> activateApplications) {
    public static ProfileSettingsDto from(Profile profile) {
        var apps = profile.getActivateApplications();
        return new ProfileSettingsDto(profile.getName(), profile.isMainProfile(), profile.isBaseLayer(), profile.isFocusBackOnLost(),
                new ArrayList<>(apps == null ? List.of() : apps));
    }
}
