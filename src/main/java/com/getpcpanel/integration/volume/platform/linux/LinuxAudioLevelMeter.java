package com.getpcpanel.integration.volume.platform.linux;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.platform.AudioLevelMeter;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.linux.LinuxRecorder.Target;
import com.getpcpanel.platform.LinuxBuild;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Meters through {@link LinuxRecorder}: one recording per thing a light shows, an app's stream or a device (an output's
 * monitor, or an input), as low-rate mono floats whose loudest sample since the last read is the peak. A recording
 * starts when a light first asks for it and stops once nothing has asked for a few seconds.
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
    private static final int LATENCY_MS = 20;

    @Inject LinuxRecorder recorder;

    private final Map<String, Recording> recordings = new HashMap<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    private boolean unavailableLogged;

    @Override
    public boolean supported() {
        var available = recorder.available();
        if (!available && !unavailableLogged) {
            unavailableLogged = true;
            log.info("parec is not available; audio-level lights show their loud colour (install pulseaudio-utils)");
        }
        return available;
    }

    @Override
    public synchronized Levels sample() {
        stopIdle(System.currentTimeMillis());
        return new Levels() {
            @Override
            public float session(AudioSession session) {
                return session instanceof PulseAudioAudioSession s ? peak("stream:" + s.index(), Target.stream(s.index())) : 0;
            }

            @Override
            public float device(String deviceId) {
                if (StringUtils.isBlank(deviceId)) {
                    return defaultOutput();
                }
                var source = deviceId.startsWith(SndCtrlPulseAudio.INPUT_PREFIX)
                        ? deviceId.substring(SndCtrlPulseAudio.INPUT_PREFIX.length())
                        : deviceId + ".monitor";
                return peak("device:" + source, Target.source(source));
            }

            @Override
            public float defaultOutput() {
                return peak("device:@DEFAULT_MONITOR@", Target.source("@DEFAULT_MONITOR@"));
            }
        };
    }

    private synchronized float peak(String key, Target target) {
        var now = System.currentTimeMillis();
        var recording = recordings.get(key);
        if (recording != null && !recording.recording.isAlive()) {
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
    private Recording start(Target target) {
        var peak = new AtomicInteger();
        var recording = recorder.start(target, CLIENT_NAME, RATE, LATENCY_MS, samples -> {
            var loud = loudest(samples);
            peak.accumulateAndGet(Float.floatToIntBits(loud), (a, b) -> Float.intBitsToFloat(a) >= Float.intBitsToFloat(b) ? a : b);
        });
        return recording == null ? null : new Recording(recording, peak);
    }

    private void stopIdle(long now) {
        var stale = new ArrayList<String>();
        recordings.forEach((key, r) -> {
            if (now - r.usedAt > IDLE_MS) {
                stale.add(key);
            }
        });
        stale.forEach(key -> recordings.remove(key).recording.stop());
    }

    @PreDestroy
    synchronized void stop() {
        recordings.values().forEach(r -> r.recording.stop());
        recordings.clear();
    }

    /** The loudest sample of a piece of audio, clipped at full scale. */
    static float loudest(FloatBuffer samples) {
        var max = 0f;
        while (samples.hasRemaining()) {
            max = Math.max(max, Math.abs(samples.get()));
        }
        return Math.min(max, 1f);
    }

    private static final class Recording {
        final LinuxRecorder.Recording recording;
        /** The loudest sample since the last read, as float bits; 0 is silence. */
        final AtomicInteger peak;
        volatile long usedAt;

        Recording(LinuxRecorder.Recording recording, AtomicInteger peak) {
            this.recording = recording;
            this.peak = peak;
        }
    }
}
