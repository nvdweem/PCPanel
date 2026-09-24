package com.getpcpanel.integration.sonar;

/**
 * The two independent mixes Streamer mode exposes per channel. Classic mode has neither. The constant is
 * Sonar's API id; {@link #label()} is the name GG shows for it.
 */
public enum SonarMix {
    monitoring("Personal Mix"),
    streaming("Stream Mix");

    private final String label;

    SonarMix(String label) {
        this.label = label;
    }

    public String apiId() {
        return name();
    }

    public String label() {
        return label;
    }
}
