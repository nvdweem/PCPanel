package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.LoopbackCapture;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Records what an output plays through {@code parec} on its monitor, or an input (a source) directly, as mono floats at
 * {@link #RATE}: the sound server does the downmix and the rate change. A reader thread fills a ring buffer that
 * {@link #read} drains. When the default device it follows changes the recording reports itself broken, so the caller
 * restarts it on the new one.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxLoopbackCapture implements LoopbackCapture {
    /** The application name of the recording, so the microphone alert can tell it apart from a real recording. */
    static final String CLIENT_NAME = "PCPanel visualizer";
    static final int RATE = 22_050;
    private static final int RING = RATE; // a second
    private static final int CHUNK = RATE / 40 * Float.BYTES; // 25 ms
    private static final long DEFAULT_CHECK_MS = 2_000;

    @Inject ProcessHelper processes;
    @Inject ISndCtrl sndCtrl;

    private final float[] ring = new float[RING];
    private int head;
    private int size;
    @Nullable private Process process;
    @Nullable private Boolean available;
    @Nullable private String defaultDevice;
    private boolean followsDefault;
    private boolean input;
    private long defaultCheckedAt;

    @Override
    public boolean supported() {
        if (available == null) {
            available = parecRuns();
        }
        return available;
    }

    private boolean parecRuns() {
        try {
            return processes.run(Duration.ofSeconds(3), "parec", "--version").succeeded();
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    @Nullable
    public String unavailableReason() {
        return supported() ? null : "The music visualizer needs parec. Install pulseaudio-utils (it also provides pactl).";
    }

    @Override
    public synchronized boolean start(@Nullable String deviceId, boolean input) {
        stop();
        try {
            var p = processes.startReading(command(deviceId, input).toArray(String[]::new));
            process = p;
            followsDefault = deviceId == null;
            this.input = input;
            defaultDevice = currentDefault();
            defaultCheckedAt = System.currentTimeMillis();
            var reader = new Thread(() -> readFrom(p, p.getInputStream()), "visualizer-parec");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (IOException e) {
            log.debug("Unable to start parec for the visualizer: {}", e.toString());
            return false;
        }
    }

    /**
     * Records an output's monitor ({@code deviceId} its sink name, null the default output) or an input itself
     * ({@code deviceId} its source name, null the default input).
     */
    static List<String> command(@Nullable String deviceId, boolean input) {
        return List.of("parec", "--device=" + parecDevice(deviceId, input), "--client-name=" + CLIENT_NAME, "--format=float32le", "--channels=1", "--rate=" + RATE,
                "--raw", "--latency-msec=25"); // smaller hand-overs, so a 50 ms frame rarely finds nothing
    }

    static String parecDevice(@Nullable String deviceId, boolean input) {
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
        if (process != null) {
            ProcessHelper.stop(process);
            process = null;
        }
        head = 0;
        size = 0;
    }

    @Override
    public int sampleRate() {
        return RATE;
    }

    @Override
    public synchronized int read(float[] into) {
        if (process == null) {
            return 0;
        }
        if (!process.isAlive() || defaultChanged()) {
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

    private void readFrom(Process owner, InputStream in) {
        var bytes = new byte[CHUNK];
        var floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        try (in) {
            int n;
            // Whole chunks, so a read never splits a sample.
            while ((n = in.readNBytes(bytes, 0, CHUNK)) > 0) {
                synchronized (this) {
                    if (process != owner) {
                        return;
                    }
                    for (var i = 0; i < n / Float.BYTES; i++) {
                        ring[head] = floats.get(i);
                        head = (head + 1) % RING;
                        size = Math.min(size + 1, RING); // when nobody reads, the oldest is overwritten
                    }
                }
            }
        } catch (IOException e) {
            // the recording was stopped
        }
    }
}
