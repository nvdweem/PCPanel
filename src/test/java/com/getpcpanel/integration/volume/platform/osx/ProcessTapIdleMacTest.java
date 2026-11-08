package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * A real process tap on a child process that plays silence on command: its aggregate starts rendering with the first
 * sound, goes back to waiting once the child stops, and starts again with the next sound. The IO proc runs without the
 * System Audio Recording consent too (the tap then delivers silence), so this needs only a default output device.
 * Enable with {@code -Dpcpanel.mactest=true}.
 */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class ProcessTapIdleMacTest {
    private static final long TIMEOUT_MS = 5_000;

    @Test
    void anIdleTapWaitsForTheNextSoundAndResumesWithIt() throws Exception {
        var java = ProcessHandle.current().info().command().orElseThrow();
        var child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SilentPlayer.class.getName())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try (var commands = new PrintWriter(child.getOutputStream(), true, StandardCharsets.UTF_8);
             var replies = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("ready", replies.readLine());
            var wrapper = new CoreAudioWrapper();
            var processObject = wrapper.translatePidToProcessObject((int) child.pid());
            assertNotEquals(0, processObject, "the child is a HAL process");
            var deaths = new AtomicInteger();

            try (var tap = ProcessTap.create(processObject, wrapper.deviceUid(wrapper.getDefaultDevice(true)), 0.5f, false)) {
                tap.whenDead(deaths::incrementAndGet);
                Thread.sleep(1_000);
                assertFalse(tap.renderedSinceArmed(), "waits for the first sound");

                commands.println("play");
                assertTrue(eventually(tap::renderedSinceArmed), "renders while the child plays");

                commands.println("stop");
                assertTrue(eventually(() -> !tap.renderedSinceArmed()), "waits again once the child is quiet");
                Thread.sleep(1_000);
                assertFalse(tap.renderedSinceArmed(), "stays waiting while the child is quiet");

                commands.println("play");
                assertTrue(eventually(tap::renderedSinceArmed), "renders again with the next sound");
                assertTrue(tap.alive());
                assertEquals(0, deaths.get());
            }
        } finally {
            child.destroy();
        }
    }

    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    /** Plays silence on the default output between "play" and "stop" lines on stdin. */
    public static final class SilentPlayer {
        public static void main(String[] args) throws Exception {
            var device = new CoreAudioWrapper().getDefaultDevice(true);
            var lib = CoreAudioTapLib.INSTANCE;
            CoreAudioTapLib.AudioDeviceIOProc proc = (dev, now, input, inputTime, output, outputTime, data) -> silence(output);
            var procId = new PointerByReference();
            if (lib.AudioDeviceCreateIOProcID(device, proc, null, procId) != 0) {
                throw new IllegalStateException("no IO proc on device " + device);
            }
            System.out.println("ready");
            var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (var line = in.readLine(); line != null; line = in.readLine()) {
                switch (line) {
                    case "play" -> lib.AudioDeviceStart(device, procId.getValue());
                    case "stop" -> lib.AudioDeviceStop(device, procId.getValue());
                    default -> { }
                }
            }
            lib.AudioDeviceStop(device, procId.getValue());
            lib.AudioDeviceDestroyIOProcID(device, procId.getValue());
            Reference.reachabilityFence(proc);
        }

        private static int silence(Pointer outputData) {
            if (outputData != null) {
                for (var i = 0; i < outputData.getInt(0); i++) {
                    var offset = 8 + i * 16L;
                    var buffer = outputData.getPointer(offset + 8);
                    if (buffer != null) {
                        buffer.setMemory(0, outputData.getInt(offset + 4), (byte) 0);
                    }
                }
            }
            return 0;
        }
    }
}
