package com.getpcpanel.integration.sonar;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * A write target resolved against the live mode. Classic mode has no mixes, so {@link #of} drops the
 * mix there — which makes the two mixes of one channel compare equal, and is what collapses a control
 * carrying both into a single pending write. The record's own equality is the coalescing key.
 */
public record SonarRoute(SonarMode mode, @Nullable SonarMix mix, SonarChannel channel) {
    public SonarRoute {
        if (mode == SonarMode.classic) {
            mix = null;
        }
    }

    public static SonarRoute of(SonarMode mode, SonarMix mix, SonarChannel channel) {
        return new SonarRoute(mode, mix, channel);
    }

    public String volumePath(double value) {
        // Locale.ROOT: a comma decimal separator would produce a path Sonar cannot parse.
        return base() + "/Volume/" + String.format(Locale.ROOT, "%.4f", value);
    }

    public String mutePath(boolean muted) {
        // Sonar names the mute segment differently per mode; the other spelling answers 404.
        return base() + (mode == SonarMode.classic ? "/Mute/" : "/isMuted/") + muted;
    }

    private String base() {
        return mode == SonarMode.classic
                ? "/volumeSettings/classic/" + channel.apiId()
                : "/volumeSettings/streamer/" + mix.apiId() + "/" + channel.apiId();
    }
}
