package com.getpcpanel.rest.model.dto;

import com.getpcpanel.profile.Profile;

public record ProfileDto(String name, boolean isMainProfile) {
    public static ProfileDto from(Profile profile) {
        return new ProfileDto(profile.getName(), profile.isMainProfile());
    }
}
