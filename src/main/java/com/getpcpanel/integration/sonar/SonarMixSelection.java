package com.getpcpanel.integration.sonar;

import java.util.List;

/**
 * Which mix a Sonar command drives: one of the two, or both at once. Only a command carries this; a
 * {@link SonarRoute} always names a single {@link SonarMix}, so {@link #mixes()} is the one way from a
 * selection to the routes it writes. In Classic mode the two mixes resolve to the same route, so
 * {@link #both} still sends a single write there.
 */
public enum SonarMixSelection {
    monitoring(SonarMix.monitoring.label(), List.of(SonarMix.monitoring)),
    streaming(SonarMix.streaming.label(), List.of(SonarMix.streaming)),
    both("Both mixes", List.of(SonarMix.monitoring, SonarMix.streaming));

    private final String label;
    private final List<SonarMix> mixes;

    SonarMixSelection(String label, List<SonarMix> mixes) {
        this.label = label;
        this.mixes = mixes;
    }

    public String label() {
        return label;
    }

    public List<SonarMix> mixes() {
        return mixes;
    }
}
