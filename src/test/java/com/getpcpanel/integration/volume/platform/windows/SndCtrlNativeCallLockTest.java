package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.Role;

/**
 * Guards the one rule that keeps the audio layer from deadlocking: <b>no call into {@code SndCtrl.dll}
 * may be made while the {@code devices} monitor is held.</b>
 *
 * <p>The DLL takes its own {@code g_audioMutex} and, still holding it, calls back up into Java
 * ({@code deviceAdded} / {@code deviceRemoved}) — and those callbacks synchronize on {@code devices}.
 * So a Java thread that holds {@code devices} and then enters the DLL forms an AB-BA cycle with the
 * DLL's notification thread, and both park forever. That is fatal well beyond the audio layer: every
 * dial and button on every device is executed by the single {@code Command Handler Thread}, and
 * {@code cmd.run()} has neither a timeout nor anything to throw — so one such hang silently kills all
 * hardware control until the application is restarted, without logging a line.
 *
 * <p>Each test drives a real operation and, at the instant the native call would be made, checks from
 * another thread that {@code devices} can still be acquired. Holding it there is exactly the deadlock.
 */
class SndCtrlNativeCallLockTest {
    private static final String DEVICE_ID = "device-1";
    private static final int PID = 4321;

    @Test
    void setProcessVolumeDoesNotHoldTheDevicesLockWhileCallingNative() {
        var snd = withOneSession();

        snd.setProcessVolume("chrome.exe", "*", 0.5f);

        assertTrue(snd.nativeCalls > 0, "the operation never reached the native call, so it proves nothing");
        assertTrue(snd.lockWasFreeOnEveryNativeCall, "devices monitor was held while calling into SndCtrl.dll");
    }

    @Test
    void muteProcessesDoesNotHoldTheDevicesLockWhileCallingNative() {
        var snd = withOneSession();

        snd.muteProcesses(Set.of("chrome.exe"), MuteType.mute);

        assertTrue(snd.nativeCalls > 0, "the operation never reached the native call, so it proves nothing");
        assertTrue(snd.lockWasFreeOnEveryNativeCall, "devices monitor was held while calling into SndCtrl.dll");
    }

    @Test
    void setDefaultDeviceByNameDoesNotHoldTheDevicesLockWhileCallingNative() {
        var snd = withOneSession();

        snd.setDefaultDevice("Speakers", DataFlow.dfRender, Role.roleMultimedia);

        assertTrue(snd.nativeCalls > 0, "the operation never reached the native call, so it proves nothing");
        assertTrue(snd.lockWasFreeOnEveryNativeCall, "devices monitor was held while calling into SndCtrl.dll");
    }

    private static LockProbingSndCtrl withOneSession() {
        var snd = new LockProbingSndCtrl();
        // deviceAdded is the real JNI callback, so this populates devices exactly as the DLL would.
        var device = (WindowsAudioDevice) snd.deviceAdded("Speakers", DEVICE_ID, 1f, false, DataFlow.dfRender.ordinal());
        // Inserted directly rather than via addSession, which fires a CDI event we have no bus for here.
        device.getSessions().put(PID, new WindowsAudioSession(device, null, PID, new File("chrome.exe"), "Chrome", null, 1f, false));
        return snd;
    }

    /** Records, at each point the real class would enter the DLL, whether {@code devices} was free. */
    private static final class LockProbingSndCtrl extends SndCtrlWindows {
        private int nativeCalls;
        private boolean lockWasFreeOnEveryNativeCall = true;

        @Override
        public void setProcessVolume(WindowsAudioSession session, float volume) {
            recordNativeCall();
        }

        @Override
        public void muteProcess(WindowsAudioSession session, MuteType muted) {
            recordNativeCall();
        }

        @Override
        void applyDefaultDevice(String deviceId, DataFlow flow, Role role) {
            recordNativeCall();
        }

        private void recordNativeCall() {
            nativeCalls++;
            lockWasFreeOnEveryNativeCall &= devicesLockIsFree();
        }

        /**
         * Whether another thread can take the {@code devices} monitor right now. {@code getDevicesMap}
         * synchronizes on it, standing in for the {@code deviceAdded} callback the DLL makes while
         * holding {@code g_audioMutex}.
         */
        private boolean devicesLockIsFree() {
            var acquired = new CountDownLatch(1);
            var prober = new Thread(() -> {
                getDevicesMap();
                acquired.countDown();
            }, "devices-lock-probe");
            prober.setDaemon(true);
            prober.start();
            try {
                return acquired.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
