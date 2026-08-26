package com.getpcpanel.integration.volume.mutecolor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDeviceMute;
import com.getpcpanel.integration.volume.command.CommandVolumeFocusMute;
import com.getpcpanel.integration.volume.command.CommandVolumeProcessMute;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * A control can be muted by a dedicated mute <em>button</em> ("Device mute", "App mute",
 * "Focused-app mute") without any volume dial on the same control. The mute-override colour follows
 * those buttons' own targets, so pressing the button changes the LED just like a volume dial does.
 */
class VolumeMuteResolverTest {
    private static Commands commands(Command... cmds) {
        return new Commands(List.of(cmds), CommandsType.allAtOnce);
    }

    // ── device mute button ──────────────────────────────────────────────────────
    @Test
    void deviceMuteButtonAloneFollowsItsOwnDevice() {
        var resolver = new DeviceMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withDevice("id-mic", "Microphone", true);

        var muted = resolver.resolve(commands(new CommandVolumeDeviceMute("id-mic", MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "a Device mute button must resolve its own device's mute state");
        assertEquals(true, muted.get());
    }

    @Test
    void deviceMuteButtonWithEmptyDeviceIdFollowsTheDefaultDevice() {
        var resolver = new DeviceMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withDevice("id-default", "Speakers", true).withDefaultDevice("id-default");

        var muted = resolver.resolve(commands(new CommandVolumeDeviceMute("", MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "an empty device id means the default device");
        assertEquals(true, muted.get());
    }

    @Test
    void unmutedDeviceMuteButtonResolvesToNotMuted() {
        var resolver = new DeviceMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withDevice("id-mic", "Microphone", false);

        var muted = resolver.resolve(commands(new CommandVolumeDeviceMute("id-mic", MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent());
        assertEquals(false, muted.get());
    }

    @Test
    void theVolumeDialWinsOverAMuteButtonOnTheSameControl() {
        var resolver = new DeviceMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withDevice("id-dial", "Speakers", true).withDevice("id-button", "Microphone", false);

        // Turning the knob controls the speakers; pressing it mutes the mic. The LED tracks the knob's
        // own (dial) target, which is the control's primary purpose.
        var muted = resolver.resolve(commands(
                new CommandVolumeDevice("id-dial", false, null),
                new CommandVolumeDeviceMute("id-button", MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent());
        assertEquals(true, muted.get(), "the dial's device (muted) drives the colour, not the button's");
    }

    // ── app mute button ─────────────────────────────────────────────────────────
    @Test
    void appMuteButtonAloneFollowsItsOwnProcesses() {
        var resolver = new ProcessMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withSession("chrome.exe", true);

        var muted = resolver.resolve(commands(new CommandVolumeProcessMute(Set.of("chrome.exe"), MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "an App mute button must resolve its own processes' mute state");
        assertEquals(true, muted.get());
    }

    // ── focused-app mute button ─────────────────────────────────────────────────
    @Test
    void focusMuteButtonFollowsTheFocusedApplication() {
        var resolver = new ProcessMuteResolver();
        resolver.sndCtrl = new FakeSndCtrl().withSession("chrome.exe", true).withFocusApplication("chrome.exe");

        var muted = resolver.resolve(commands(new CommandVolumeFocusMute(MuteType.toggle)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "a Focused-app mute button must resolve the focused app's mute state");
        assertEquals(true, muted.get());
    }

    // ── test doubles ────────────────────────────────────────────────────────────
    private static final class MutableDevice extends AudioDevice {
        MutableDevice(String name, String id, boolean muted) {
            super(null, name, id);
            muted(muted);
        }
    }

    private static final class FakeSndCtrl implements ISndCtrl {
        private final List<AudioDevice> devices = new ArrayList<>();
        private final List<AudioSession> sessions = new ArrayList<>();
        private String defaultDevice = "";
        private String focusApplication;

        FakeSndCtrl withDevice(String id, String name, boolean muted) {
            devices.add(new MutableDevice(name, id, muted));
            return this;
        }

        FakeSndCtrl withDefaultDevice(String id) {
            defaultDevice = id;
            return this;
        }

        FakeSndCtrl withSession(String exe, boolean muted) {
            sessions.add(new AudioSession(null, 1234, new File("C:\\apps\\" + exe), exe, null, 1f, muted));
            return this;
        }

        FakeSndCtrl withFocusApplication(String exe) {
            focusApplication = exe;
            return this;
        }

        @Override public Map<String, AudioDevice> getDevicesMap() {
            return devices.stream().collect(Collectors.toMap(AudioDevice::id, d -> d));
        }

        @Override public Collection<AudioDevice> devices() {
            return devices;
        }

        @Override public Collection<AudioSession> getAllSessions() {
            return sessions;
        }

        @Override public AudioDevice getDevice(String id) {
            return devices.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
        }

        @Override public void setDeviceVolume(String deviceId, float volume) {
        }

        @Override public void muteDevice(String deviceId, MuteType mute) {
        }

        @Override public void setDefaultDevice(String deviceId) {
        }

        @Override public void setProcessVolume(String fileName, String device, float volume) {
        }

        @Override public void setFocusVolume(float volume) {
        }

        @Override public void muteProcesses(Set<String> fileName, MuteType mute) {
        }

        @Override public String getFocusApplication() {
            return focusApplication;
        }

        @Override public List<RunningApplication> getRunningApplications() {
            return List.of();
        }

        @Override public String defaultDeviceOnEmpty(String deviceId) {
            return deviceId == null || deviceId.isBlank() ? defaultDevice : deviceId;
        }

        @Override public String defaultPlayer() {
            return defaultDevice;
        }

        @Override public String defaultRecorder() {
            return defaultDevice;
        }
    }
}
