package com.getpcpanel.integration.sonar;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

/** A snapshot of Sonar, keyed by resolved route so lookups match the write path exactly. */
public record SonarState(@Nullable SonarMode mode, Map<SonarRoute, SonarLevel> levels) {
    public static final SonarState UNKNOWN = new SonarState(null, Map.of());

    public Optional<SonarLevel> level(SonarRoute route) {
        return Optional.ofNullable(levels.get(route));
    }
}
