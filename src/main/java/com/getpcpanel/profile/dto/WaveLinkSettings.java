package com.getpcpanel.profile.dto;

public record WaveLinkSettings(boolean enabled) {
    public static final WaveLinkSettings DEFAULT = new WaveLinkSettings(false);
}
