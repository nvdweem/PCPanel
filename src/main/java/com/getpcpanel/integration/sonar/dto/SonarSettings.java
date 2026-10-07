package com.getpcpanel.integration.sonar.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param enabled          SteelSeries Sonar integration on/off. Off by default, like Wave Link: it polls a
 *                         local service, so it should only run when the user asks for it.
 * @param updatesPerSecond how many volume writes a second each Sonar route gets at most. Clamped into
 *                         {@value #MIN_UPDATES_PER_SECOND}–{@value #MAX_UPDATES_PER_SECOND}; a save without
 *                         it, or with null, reads as {@value #DEFAULT_UPDATES_PER_SECOND}.
 */
public record SonarSettings(boolean enabled, int updatesPerSecond) {
    public static final int MIN_UPDATES_PER_SECOND = 6;
    public static final int MAX_UPDATES_PER_SECOND = 25;
    public static final int DEFAULT_UPDATES_PER_SECOND = 12;
    public static final SonarSettings DEFAULT = new SonarSettings(false, DEFAULT_UPDATES_PER_SECOND);

    public SonarSettings {
        updatesPerSecond = Math.clamp(updatesPerSecond, MIN_UPDATES_PER_SECOND, MAX_UPDATES_PER_SECOND);
    }

    /** A save without a rate (or with a null one) reads as the default rate. */
    @JsonCreator
    public static SonarSettings fromJson(@JsonProperty("enabled") boolean enabled,
                                         @JsonProperty("updatesPerSecond") @Nullable Integer updatesPerSecond) {
        return new SonarSettings(enabled, updatesPerSecond != null ? updatesPerSecond : DEFAULT_UPDATES_PER_SECOND);
    }
}
