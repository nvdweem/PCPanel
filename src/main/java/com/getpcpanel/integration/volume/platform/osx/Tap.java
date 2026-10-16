package com.getpcpanel.integration.volume.platform.osx;

/**
 * A live per-process volume control rendering one process's audio to one output device; see {@link ProcessTap}.
 * Public so Mockito can mock {@link Factory} under the native-image tracing agent (the Windows CI test run), which
 * otherwise yields mocks that don't implement {@code create}.
 */
public interface Tap extends AutoCloseable {
    void setGain(float gain);

    void setMuted(boolean muted);

    @Override
    void close();

    /** Creates a tap on a HAL process object rendering to the output device with the given UID. */
    @FunctionalInterface
    interface Factory {
        /**
         * The gain and mute are in place before the tap renders anything.
         *
         * @throws ProcessTap.TapCreationException when CoreAudio refuses the tap
         */
        Tap create(int processObject, String outputDeviceUid, float initialGain, boolean initialMuted);
    }
}
