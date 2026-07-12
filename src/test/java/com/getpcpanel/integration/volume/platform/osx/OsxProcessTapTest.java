package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * End-to-end hardware-free validation of the process-tap JNA bindings, mirroring what a user hears:
 * BlackHole (a virtual loopback device) is made the default output, {@code afplay} plays a tone into it,
 * and a recorder IO proc on BlackHole measures what "reaches the speaker" while a {@link ProcessTap}
 * (and then the {@link OsxProcessTapService}) attenuates the player.
 *
 * <p>Requires macOS 14.4+, an installed BlackHole 16ch, and the System Audio Recording TCC consent
 * (pre-granted in CI); enable with {@code -Dpcpanel.mactest=true}. See the citest workflow.
 */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class OsxProcessTapTest {
    private static final String BLACKHOLE_UID = "BlackHole16ch_UID";
    private static final double AUDIBLE = 0.003;
    private static final int SAMPLE_RATE = 48_000;

    @Test
    @DisplayName("process tap attenuates, silences and releases a process's audio")
    void tapControlsProcessVolume() throws Exception {
        assertTrue(OsxProcessTapService.isSupported(), "macOS version supports process taps");

        var wrapper = new CoreAudioWrapper();
        var blackhole = wrapper.getDevices().stream().filter(d -> BLACKHOLE_UID.equals(d.uid()) && d.output()).findFirst().orElse(null);
        assertNotNull(blackhole, "BlackHole 16ch virtual device is installed");
        log("BlackHole device id " + blackhole.id());
        wrapper.setDefaultDevice(blackhole.id(), true);

        var player = startTonePlayer();
        var recorder = new LoopbackRecorder(blackhole.id());
        try {
            var baseline = recorder.measure("baseline (untapped)");
            assertTrue(baseline > AUDIBLE, "tone is audible through BlackHole, rms=" + baseline);

            var processObject = waitForProcessObject(wrapper, (int) player.pid());
            assertNotEquals(0, processObject, "afplay pid translates to a HAL process object");

            var rms1 = runTapPhases(processObject, recorder, baseline);
            runServicePhases(wrapper, (int) player.pid(), recorder, baseline, rms1);
        } finally {
            recorder.close();
            player.destroy();
        }
    }

    /** Direct {@link ProcessTap} phases: explicit gains against a tap-active reference level. */
    private static double runTapPhases(int processObject, LoopbackRecorder recorder, double baseline) {
        double rms1;
        try (var tap = ProcessTap.create(processObject, BLACKHOLE_UID, 1)) {
            rms1 = recorder.measure("tap active, gain 1.0");
            assertTrue(rms1 > AUDIBLE, "playthrough reaches the output, rms=" + rms1);

            tap.setGain(0.25f);
            var rms2 = recorder.measure("gain 0.25");
            var ratio = rms2 / rms1;
            assertTrue(ratio > 0.15 && ratio < 0.40, "gain scales the audible volume (~0.25 expected), ratio=" + ratio);

            tap.setGain(0);
            var rms3 = recorder.measure("gain 0.0");
            assertTrue(rms3 < Math.max(0.002, rms1 * 0.05), "direct path is muted, so gain 0 is silence, rms=" + rms3);
        }
        var rms4 = recorder.measure("tap closed");
        assertTrue(rms4 > baseline * 0.5, "closing the tap restores the app's own output, rms=" + rms4 + " vs baseline=" + baseline);
        return rms1;
    }

    /** Service-level phases: volume below full creates a tap, full volume removes it again. */
    private static void runServicePhases(CoreAudioWrapper wrapper, int pid, LoopbackRecorder recorder, double baseline, double tapReference) {
        var service = new OsxProcessTapService(wrapper);
        try {
            service.setVolume(pid, 0.25f);
            var attenuated = recorder.measure("service volume 0.25");
            assertTrue(attenuated < baseline * 0.5, "service attenuates the app, rms=" + attenuated + " vs baseline=" + baseline);
            assertTrue(attenuated < tapReference, "attenuated below the tap-active full-gain level");

            service.setVolume(pid, 1);
            var restored = recorder.measure("service volume 1.0 (tap removed)");
            assertTrue(restored > baseline * 0.5, "full volume removes the tap and restores direct output, rms=" + restored);
        } finally {
            service.destroy();
        }
    }

    private static int waitForProcessObject(CoreAudioWrapper wrapper, int pid) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 10_000;
        var processObject = 0;
        while (processObject == 0 && System.currentTimeMillis() < deadline) {
            processObject = wrapper.translatePidToProcessObject(pid);
            if (processObject == 0) {
                Thread.sleep(250);
            }
        }
        log("pid " + pid + " -> process object " + processObject);
        return processObject;
    }

    private static Process startTonePlayer() throws IOException {
        var wav = Files.createTempFile("pcpanel-tone", ".wav");
        Files.write(wav, sineWav(60));
        wav.toFile().deleteOnExit();
        var player = new ProcessBuilder("afplay", wav.toString()).start();
        log("afplay pid " + player.pid());
        return player;
    }

    /** A 440Hz stereo 16-bit PCM WAV, built by hand to keep the test free of javax.sound. */
    private static byte[] sineWav(int seconds) throws IOException {
        var frames = SAMPLE_RATE * seconds;
        var dataSize = frames * 2 * 2;
        var out = new ByteArrayOutputStream(44 + dataSize);
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 2)
              .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2 * 2).putShort((short) 4).putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        out.write(header.array());
        var samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        for (var frame = 0; frame < frames; frame++) {
            var value = (short) (Math.sin(2 * Math.PI * 440 * frame / SAMPLE_RATE) * 0.3 * Short.MAX_VALUE);
            samples.putShort(value).putShort(value);
        }
        out.write(samples.array());
        return out.toByteArray();
    }

    private static void log(String message) {
        System.out.println("[tap-test] " + message);
    }

    /** Records BlackHole's loopback input (i.e. whatever any client renders to it) and reports windowed RMS. */
    private static final class LoopbackRecorder implements AutoCloseable {
        private final int deviceId;
        private final Pointer procId;
        @SuppressWarnings({ "FieldCanBeLocal", "unused" }) private final CoreAudioTapLib.AudioDeviceIOProc proc; // strong ref for JNA
        private final DoubleAdder sumSquares = new DoubleAdder();
        private final LongAdder sampleCount = new LongAdder();

        LoopbackRecorder(int deviceId) {
            this.deviceId = deviceId;
            var lib = CoreAudioTapLib.INSTANCE;
            var procRef = new PointerByReference();
            proc = (device, now, inputData, inputTime, outputData, outputTime, clientData) -> {
                zeroOutputs(outputData);
                accumulate(inputData);
                return 0;
            };
            var status = lib.AudioDeviceCreateIOProcID(deviceId, proc, null, procRef);
            assertTrue(status == 0, "recorder AudioDeviceCreateIOProcID, status=" + status);
            procId = procRef.getValue();
            status = lib.AudioDeviceStart(deviceId, procId);
            assertTrue(status == 0, "recorder AudioDeviceStart, status=" + status);
        }

        private void accumulate(Pointer inputData) {
            if (inputData == null) {
                return;
            }
            var buffers = inputData.getInt(0);
            for (var i = 0; i < buffers; i++) {
                var offset = 8 + (long) i * 16;
                var data = inputData.getPointer(offset + 8);
                var floats = inputData.getInt(offset + 4) / 4;
                if (data == null || floats == 0) {
                    continue;
                }
                var samples = data.getFloatArray(0, floats);
                double sum = 0;
                for (var sample : samples) {
                    sum += (double) sample * sample;
                }
                sumSquares.add(sum);
                sampleCount.add(floats);
            }
        }

        private static void zeroOutputs(Pointer outputData) {
            if (outputData == null) {
                return;
            }
            var buffers = outputData.getInt(0);
            for (var i = 0; i < buffers; i++) {
                var offset = 8 + (long) i * 16;
                var data = outputData.getPointer(offset + 8);
                if (data != null) {
                    data.setMemory(0, outputData.getInt(offset + 4), (byte) 0);
                }
            }
        }

        /** Lets the previous phase settle, then measures RMS over a 2s window. */
        double measure(String phase) {
            sleep(600);
            sumSquares.reset();
            sampleCount.reset();
            sleep(2_000);
            var count = sampleCount.sum();
            var rms = count == 0 ? 0 : Math.sqrt(sumSquares.sum() / count);
            log(String.format("phase %s: rms=%.5f (%d samples)", phase, rms, count));
            return rms;
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            var lib = CoreAudioTapLib.INSTANCE;
            lib.AudioDeviceStop(deviceId, procId);
            lib.AudioDeviceDestroyIOProcID(deviceId, procId);
        }
    }
}
