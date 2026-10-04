package com.getpcpanel.integration.volume.platform;

import javax.annotation.Nullable;

/**
 * What an output plays (a loopback) or an input hears, as mono samples, for the music visualizer. {@link #start} opens
 * it, {@link #read} drains what arrived since the last read, {@link #stop()} closes it. Use from one thread only.
 */
public interface LoopbackCapture {
    /** Whether this platform can capture at all. */
    boolean supported();

    /** Why it can't, in words for the user; null while it can. */
    @Nullable
    String unavailableReason();

    /**
     * Opens the capture on {@code deviceId} (a device's id as {@link AudioDevice#id()} or {@link AudioSession#deviceId()}
     * gives it), or on the default device when null, following it when it changes. {@code input} picks an input (a
     * microphone, recorded directly) over an output (what it plays, by loopback). Returns whether it is open.
     */
    boolean start(@Nullable String deviceId, boolean input);

    void stop();

    /** The rate of the samples {@link #read} returns. Valid once started. */
    int sampleRate();

    /**
     * Copies the mono samples that arrived since the last read into {@code into}, up to its length; what doesn't fit
     * waits for the next read. Returns how many; 0 when nothing played or it isn't open. Returns -1 when the capture
     * broke (the device went away): stop and start it again.
     */
    int read(float[] into);
}
