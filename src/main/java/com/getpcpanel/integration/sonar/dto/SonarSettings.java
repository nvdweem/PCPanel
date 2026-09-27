package com.getpcpanel.integration.sonar.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param enabled SteelSeries Sonar integration on/off. Off by default, like Wave Link: it polls a
 *                local service, so it should only run when the user asks for it.
 */
public record SonarSettings(boolean enabled) {
    public static final SonarSettings DEFAULT = new SonarSettings(false);

    @JsonCreator
    public SonarSettings(@JsonProperty("enabled") boolean enabled) {
        this.enabled = enabled;
    }
}
