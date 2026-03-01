package com.getpcpanel.profile;

public record ElgatoSettings(boolean waveLinkEnabled, boolean controlCenterEnabled) {
    public static final ElgatoSettings DEFAULT = new ElgatoSettings(false, false);
}
