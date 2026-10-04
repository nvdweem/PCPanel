package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.platform.AudioLevelMeter;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Meters through {@code parec} (pulseaudio-utils, which also provides pactl; works on PipeWire's pulse server): one
 * recording per thing a light shows, an app's stream ({@code --monitor-stream}) or a device (an output's monitor, or
 * an input), as low-rate mono floats whose loudest sample since the last read is the peak. A recording starts when a
 * light first asks for it and stops once nothing has asked for a few seconds. In the Flatpak, parec runs on the host
 * through a wrapper, like pactl.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxAudioLevelMeter implements AudioLevelMeter {
    /** The application name the recordings carry, so the microphone alert can tell them apart from real recordings. */
    static final String CLIENT_NAME = "PCPanel level meter";
    private static final long IDLE_MS = 3_000;
    /** How long a recording that failed to start or ended waits before it is tried again. */
    private static final long RETRY_MS = 5_000;
    private static final int RATE = 8000;
    private static final int CHUNK = RATE / 50 * Float.BYTES; // 20 ms

    @Inject ProcessHelper processes;

    private final Map<String, Recording> recordings = new HashMap<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    @Nullable private Boolean available;

    @Override
    public boolean supported() {
        if (available == null) {
            available = parecRuns();
            if (!available) {
                log.info("parec is not available; audio-level lights show their loud colour (install pulseaudio-utils)");
            }
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
    public synchronized Levels sample() {
        stopIdle(System.currentTimeMillis());
        return new Levels() {
            @Override
            public float session(AudioSession session) {
                return session instanceof PulseAudioAudioSession s ? peak("stream:" + s.index(), "--monitor-stream=" + s.index()) : 0;
            }

            @Override
            public float device(String deviceId) {
                if (StringUtils.isBlank(deviceId)) {
                    return defaultOutput();
                }
                var source = deviceId.startsWith(SndCtrlPulseAudio.INPUT_PREFIX)
                        ? deviceId.substring(SndCtrlPulseAudio.INPUT_PREFIX.length())
                        : deviceId + ".monitor";
                return peak("device:" + source, "--device=" + source);
            }

            @Override
            public float defaultOutput() {
                return peak("device:@DEFAULT_MONITOR@", "--device=@DEFAULT_MONITOR@");
            }
        };
    }

    private synchronized float peak(String key, String target) {
        var now = System.currentTimeMillis();
        var recording = recordings.get(key);
        if (recording != null && !recording.process.isAlive()) {
            recordings.remove(key);
            failedAt.put(key, now);
            recording = null;
        }
        if (recording == null) {
            if (now - failedAt.getOrDefault(key, 0L) < RETRY_MS) {
                return 0;
            }
            recording = start(target);
            if (recording == null) {
                failedAt.put(key, now);
                return 0;
            }
            recordings.put(key, recording);
        }
        recording.usedAt = now;
        return Float.intBitsToFloat(recording.peak.getAndSet(0));
    }

    @Nullable
    private Recording start(String target) {
        try {
            var process = processes.startReading(command(target).toArray(String[]::new));
            var recording = new Recording(process);
            var reader = new Thread(() -> recording.read(process.getInputStream()), "parec " + target);
            reader.setDaemon(true);
            reader.start();
            return recording;
        } catch (IOException e) {
            log.debug("Unable to start parec for {}: {}", target, e.toString());
            return null;
        }
    }

    static List<String> command(String target) {
        return List.of("parec", target, "--client-name=" + CLIENT_NAME, "--format=float32le", "--channels=1", "--rate=" + RATE, "--raw",
                "--latency-msec=20");
    }

    private void stopIdle(long now) {
        var stale = new ArrayList<String>();
        recordings.forEach((key, r) -> {
            if (now - r.usedAt > IDLE_MS) {
                stale.add(key);
            }
        });
        stale.forEach(key -> ProcessHelper.stop(recordings.remove(key).process));
    }

    @PreDestroy
    synchronized void stop() {
        recordings.values().forEach(r -> ProcessHelper.stop(r.process));
        recordings.clear();
    }

    /** The loudest sample of a chunk of little-endian floats. */
    static float loudest(byte[] bytes, int length) {
        var buffer = ByteBuffer.wrap(bytes, 0, length - length % Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        var max = 0f;
        while (buffer.hasRemaining()) {
            max = Math.max(max, Math.abs(buffer.get()));
        }
        return Math.min(max, 1f);
    }

    private static final class Recording {
        final Process process;
        /** The loudest sample since the last read, as float bits; 0 is silence. */
        final AtomicInteger peak = new AtomicInteger();
        volatile long usedAt;

        Recording(Process process) {
            this.process = process;
        }

        void read(InputStream in) {
            var bytes = new byte[CHUNK];
            try (in) {
                int n;
                // Whole chunks, so a read never splits a sample.
                while ((n = in.readNBytes(bytes, 0, CHUNK)) > 0) {
                    var loud = loudest(bytes, n);
                    peak.accumulateAndGet(Float.floatToIntBits(loud), (a, b) -> Float.intBitsToFloat(a) >= Float.intBitsToFloat(b) ? a : b);
                }
            } catch (IOException e) {
                // the recording was stopped
            }
        }
    }
}
