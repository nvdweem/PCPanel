package com.getpcpanel.integration.sonar;

/** Sonar state changed: a mute flipped, levels were polled, or the mode was found or lost. Observed by {@code MuteColorService}. */
public record SonarChangedEvent() {}
