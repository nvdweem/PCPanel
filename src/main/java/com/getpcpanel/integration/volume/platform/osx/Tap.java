package com.getpcpanel.integration.volume.platform.osx;

/** A live per-process volume control rendering one process's audio to one output device; see {@link ProcessTap}. */
interface Tap extends AutoCloseable {
    void setGain(float gain);

    void setMuted(boolean muted);

    @Override
    void close();

    /** Creates a tap on a HAL process object rendering to the output device with the given UID. */
    @FunctionalInterface
    interface Factory {
        /** @throws ProcessTap.TapCreationException when CoreAudio refuses the tap */
        Tap create(int processObject, String outputDeviceUid, float initialGain);
    }
}
