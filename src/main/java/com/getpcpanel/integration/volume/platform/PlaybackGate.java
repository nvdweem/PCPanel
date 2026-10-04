package com.getpcpanel.integration.volume.platform;

import java.util.Collection;

import javax.annotation.Nullable;

/**
 * What has sound right now (apps, outputs, inputs), cheaply enough to ask a few times a second: the music visualizer
 * only captures while one of its sources has sound. System sounds and muted apps never count as an app playing.
 */
public interface PlaybackGate {
    /** One look at what plays. Use from one thread only, and the result too. */
    Playing check();

    /** What one {@link #check()} saw. */
    interface Playing {
        Playing NOTHING = new Playing() {
            @Override
            public boolean any(Collection<String> apps) {
                return false;
            }

            @Override
            public String device(Collection<String> apps) {
                return null;
            }

            @Override
            public boolean output(String deviceId) {
                return false;
            }

            @Override
            public boolean input(String deviceId) {
                return false;
            }
        };

        /** Whether any of {@code apps} (exe names or app ids) plays; an empty list is any app. */
        boolean any(Collection<String> apps);

        /**
         * The output the loudest of {@code apps} plays on (an empty list is any app), to capture there: with Wave
         * Link and similar, apps play on virtual outputs that aren't the default. Null when unknown or nothing plays.
         */
        @Nullable
        String device(Collection<String> apps);

        /** Whether the output with this id (null: the default output) has sound. */
        boolean output(@Nullable String deviceId);

        /** Whether the input with this id (null: the default input) has sound. */
        boolean input(@Nullable String deviceId);
    }
}
