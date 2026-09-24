package com.getpcpanel.integration.sonar;

/** One channel's level on one route. Volume and mute are independent in Sonar; zero volume is not mute. */
public record SonarLevel(double volume, boolean muted) {}
