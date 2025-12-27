package com.getpcpanel.audio;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class MicrophoneLevelService {
    private static final AudioFormat FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);
    private final ISndCtrl sndCtrl;
    private final Map<String, MicMonitor> monitors = new ConcurrentHashMap<>();

    public float getLevel(String deviceId) {
        var key = StringUtils.defaultString(deviceId);
        var monitor = monitors.computeIfAbsent(key, this::createMonitor);
        return monitor.getLevel();
    }

    public float getLevelDb(String deviceId) {
        var key = StringUtils.defaultString(deviceId);
        var monitor = monitors.computeIfAbsent(key, this::createMonitor);
        return monitor.getLevelDb();
    }

    private MicMonitor createMonitor(String deviceId) {
        var device = StringUtils.isBlank(deviceId) ? null : sndCtrl.getDevice(deviceId);
        var mixer = resolveMixer(device);
        if (mixer == null) {
            log.warn("No microphone mixer found for {}", device != null ? device.name() : "default");
            return MicMonitor.disabled();
        }
        return new MicMonitor(mixer);
    }

    private Mixer resolveMixer(AudioDevice device) {
        if (device != null && StringUtils.isNotBlank(device.name())) {
            var byName = findMixerByName(device.name());
            if (byName.isPresent()) {
                return byName.get();
            }
        }
        var fallback = findMixerByName("");
        return fallback.orElse(null);
    }

    private Optional<Mixer> findMixerByName(String name) {
        var lowered = name.toLowerCase();
        for (var info : AudioSystem.getMixerInfo()) {
            var mixer = AudioSystem.getMixer(info);
            var display = (info.getName() + " " + info.getDescription()).toLowerCase();
            if (display.contains(lowered) && supportsTargetLine(mixer)) {
                return Optional.of(mixer);
            }
        }
        for (var info : AudioSystem.getMixerInfo()) {
            var mixer = AudioSystem.getMixer(info);
            if (supportsTargetLine(mixer)) {
                return Optional.of(mixer);
            }
        }
        return Optional.empty();
    }

    private boolean supportsTargetLine(Mixer mixer) {
        var info = new DataLine.Info(TargetDataLine.class, FORMAT);
        return mixer.isLineSupported(info);
    }

    private static final class MicMonitor implements Runnable {
        private final Mixer mixer;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private volatile float level;
        private volatile float levelDb = -120.0f;

        static MicMonitor disabled() {
            return new MicMonitor(null);
        }

        MicMonitor(Mixer mixer) {
            this.mixer = mixer;
            if (mixer != null) {
                var thread = new Thread(this, "MicLevelMonitor-" + mixer.getMixerInfo().getName());
                thread.setDaemon(true);
                thread.start();
            }
        }

        float getLevel() {
            return level;
        }

        float getLevelDb() {
            return levelDb;
        }

        @Override
        public void run() {
            var info = new DataLine.Info(TargetDataLine.class, FORMAT);
            try (TargetDataLine line = (TargetDataLine) mixer.getLine(info)) {
                line.open(FORMAT);
                line.start();
                var buffer = new byte[2048];
                while (running.get()) {
                    var read = line.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        updateLevels(buffer, read);
                    }
                }
            } catch (Exception e) {
                log.debug("Mic monitor error: {}", e.getMessage());
            }
        }

        private void updateLevels(byte[] buffer, int length) {
            long sum = 0;
            int samples = length / 2;
            for (int i = 0; i < length; i += 2) {
                int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
                sum += sample * (long) sample;
            }
            if (samples <= 0) {
                level = 0.0f;
                levelDb = -120.0f;
                return;
            }
            double rms = Math.sqrt(sum / (double) samples);
            level = (float) Math.min(1.0, rms / 32768.0);
            if (rms <= 0.0) {
                levelDb = -120.0f;
            } else {
                levelDb = (float) (20.0 * Math.log10(rms / 32768.0));
            }
        }
    }
}
