package com.getpcpanel.integration.volume.platform.linux;

import java.nio.FloatBuffer;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.LoopbackCapture;
import com.getpcpanel.integration.volume.platform.linux.LinuxRecorder.Recording;
import com.getpcpanel.integration.volume.platform.linux.LinuxRecorder.Target;
import com.getpcpanel.platform.LinuxBuild;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Records what an output plays (its monitor), or an input (a source) directly, as mono floats at {@link #RATE} through
 * {@link LinuxRecorder}: the sound server does the downmix and the rate change. The recorded pieces fill a ring buffer
 * that {@link #read} drains. When the default device it follows changes the recording reports itself broken, so the
 * caller restarts it on the new one.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxLoopbackCapture implements LoopbackCapture {
    /** The application name of the recording, so the microphone alert can tell it apart from a real recording. */
    static final String CLIENT_NAME = "PCPanel visualizer";
    static final int RATE = 22_050;
    private static final int RING = RATE; // a second
    /** Small hand-overs, so a 50 ms frame rarely finds nothing. */
    private static final int LATENCY_MS = 25;
    private static final long DEFAULT_CHECK_MS = 2_000;

    @Inject LinuxRecorder recorder;
    @Inject ISndCtrl sndCtrl;

    private final float[] ring = new float[RING];
    private int head;
    private int size;
    @Nullable private Recording recording;
    /** Which recording the ring belongs to, so samples still arriving from a stopped one are left out. */
    private int generation;
    @Nullable private String defaultDevice;
    private boolean followsDefault;
    private boolean input;
    private long defaultCheckedAt;

    @Override
    public boolean supported() {
        return recorder.available();
    }

    @Override
    @Nullable
    public String unavailableReason() {
        return supported() ? null : "The music visualizer needs parec. Install pulseaudio-utils (it also provides pactl).";
    }

    @Override
    public synchronized boolean start(@Nullable String deviceId, boolean input) {
        stop();
        var owner = ++generation;
        recording = recorder.start(target(deviceId, input), CLIENT_NAME, RATE, LATENCY_MS, samples -> accept(owner, samples));
        if (recording == null) {
            return false;
        }
        followsDefault = deviceId == null;
        this.input = input;
        defaultDevice = currentDefault();
        defaultCheckedAt = System.currentTimeMillis();
        return true;
    }

    /**
     * Records an output's monitor ({@code deviceId} its sink name, null the default output) or an input itself
     * ({@code deviceId} its source name, null the default input).
     */
    static Target target(@Nullable String deviceId, boolean input) {
        return Target.source(sourceName(deviceId, input));
    }

    static String sourceName(@Nullable String deviceId, boolean input) {
        if (input) {
            return deviceId == null ? "@DEFAULT_SOURCE@" : deviceId;
        }
        return deviceId == null ? "@DEFAULT_MONITOR@" : deviceId + ".monitor";
    }

    @Nullable
    private String currentDefault() {
        return input ? sndCtrl.defaultRecorder() : sndCtrl.defaultPlayer();
    }

    @Override
    @PreDestroy
    public synchronized void stop() {
        if (recording != null) {
            recording.stop();
            recording = null;
        }
        generation++;
        head = 0;
        size = 0;
    }

    @Override
    public int sampleRate() {
        return RATE;
    }

    @Override
    public synchronized int read(float[] into) {
        if (recording == null) {
            return 0;
        }
        if (!recording.isAlive() || defaultChanged()) {
            return -1;
        }
        var n = Math.min(size, into.length);
        var start = (head - size + RING) % RING;
        for (var i = 0; i < n; i++) {
            into[i] = ring[(start + i) % RING];
        }
        size -= n;
        return n;
    }

    private boolean defaultChanged() {
        if (!followsDefault) {
            return false;
        }
        var now = System.currentTimeMillis();
        if (now - defaultCheckedAt < DEFAULT_CHECK_MS) {
            return false;
        }
        defaultCheckedAt = now;
        var current = currentDefault();
        return current != null && defaultDevice != null && !current.equals(defaultDevice);
    }

    private synchronized void accept(int owner, FloatBuffer samples) {
        if (owner != generation) {
            return;
        }
        while (samples.hasRemaining()) {
            ring[head] = samples.get();
            head = (head + 1) % RING;
            size = Math.min(size + 1, RING); // when nobody reads, the oldest is overwritten
        }
    }
}
