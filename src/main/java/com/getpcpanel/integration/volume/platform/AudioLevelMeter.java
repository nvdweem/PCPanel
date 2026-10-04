package com.getpcpanel.integration.volume.platform;

import java.util.Map;

/**
 * Live audio peak levels (0..1, linear) of what is playing: per app session and per audio device. Read
 * {@link #sample()} from one thread only; implementations may hold per-thread OS state.
 */
public interface AudioLevelMeter {
    /** Whether this platform can meter at all. */
    boolean supported();

    /** The current peaks; {@link Levels#NONE} when nothing can be read. */
    Levels sample();

    /**
     * One sample's peaks, asked for by what is shown: a platform that meters each source separately (Linux) only
     * starts metering what is asked for.
     */
    interface Levels {
        Levels NONE = new Snapshot(Map.of(), Map.of(), 0);

        /** The peak of one app session. */
        float session(AudioSession session);

        /** The peak of an audio device, by its id. */
        float device(String deviceId);

        /** The peak of the default output device. */
        float defaultOutput();
    }

    /**
     * Peaks read all at once.
     *
     * @param byPid         peak per process id, the loudest of its sessions
     * @param byDevice      peak per output device id
     * @param defaultOutput peak of the default output device
     */
    record Snapshot(Map<Integer, Float> byPid, Map<String, Float> byDevice, float defaultOutput) implements Levels {
        @Override
        public float session(AudioSession session) {
            return byPid.getOrDefault(session.pid(), 0f);
        }

        @Override
        public float device(String deviceId) {
            return byDevice.getOrDefault(deviceId, 0f);
        }
    }
}
