package com.getpcpanel.profile;

public record WaveLinkSettings(boolean enabled) {
    public static final WaveLinkSettings DEFAULT = new WaveLinkSettings(false);
}
