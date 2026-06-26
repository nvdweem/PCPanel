package com.getpcpanel.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;

/**
 * The decision logic of the focus-volume override redirector (issue #49): a focused source app must
 * redirect the dial to the rule's targets, match the exe basename case-insensitively (the OS reports a
 * full path), and only touch the source's own OS volume when {@code includeSource} is on.
 */
class FocusVolumeOverrideRedirectorTest {
    /** A target Command that records the dial value it receives (no CdiHelper, so it works unit-tested). */
    private static final class RecordingTarget extends Command implements DialAction {
        private Float received;
        private int calls;

        @Override
        public void execute(DialActionParameters context) {
            received = context.dial().getValue(this, 0, 1);
            calls++;
        }

        @Override
        public DialCommandParams getDialParams() {
            return DialCommandParams.DEFAULT;
        }

        @Override
        public String buildLabel() {
            return "recording-target";
        }
    }

    /** A no-op ISndCtrl that records setFocusVolume calls (the only path the redirector drives directly). */
    private static final class RecordingSnd implements ISndCtrl {
        private Float focusVolume;
        private int focusCalls;

        @Override public void setFocusVolume(float volume) { focusVolume = volume; focusCalls++; }
        @Override public Map<String, AudioDevice> getDevicesMap() { return Map.of(); }
        @Override public Collection<AudioDevice> devices() { return List.of(); }
        @Override public Collection<AudioSession> getAllSessions() { return List.of(); }
        @Override public AudioDevice getDevice(String id) { return null; }
        @Override public void setDeviceVolume(String deviceId, float volume) {}
        @Override public void muteDevice(String deviceId, MuteType mute) {}
        @Override public void setDefaultDevice(String deviceId) {}
        @Override public void setProcessVolume(String fileName, String device, float volume) {}
        @Override public void muteProcesses(Set<String> fileName, MuteType mute) {}
        @Override public String getFocusApplication() { return null; }
        @Override public List<RunningApplication> getRunningApplications() { return List.of(); }
        @Override public String defaultDeviceOnEmpty(String deviceId) { return deviceId; }
        @Override public String defaultPlayer() { return null; }
        @Override public String defaultRecorder() { return null; }
    }

    private static FocusVolumeOverrideRedirector redirector(RecordingSnd snd, FocusVolumeOverride... rules) {
        var save = new Save();
        save.setFocusVolumeOverrides(List.of(rules));
        var r = new FocusVolumeOverrideRedirector();
        r.sndCtrl = snd;
        r.saveService = new SaveService() {
            @Override
            public Save get() {
                return save;
            }
        };
        return r;
    }

    @Test
    void redirectsFocusedSourceToTargets_withoutTouchingSource() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), false);
        var r = redirector(snd, rule);

        // The OS reports the full path; matching is by exe basename, case-insensitive.
        var handled = r.handleFocusVolumeRequest("C:\\Program Files (x86)\\Steam\\Steam.exe", 0.4f);

        assertTrue(handled, "a matching source must claim the focus request");
        assertEquals(1, target.calls, "the target must be driven");
        assertEquals(0.4f, target.received, 0.01f, "the target receives the dial value");
        assertEquals(0, snd.focusCalls, "includeSource off → the source's own volume is left untouched");
    }

    @Test
    void includeSourceAlsoDrivesSourceOsVolume() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), true);
        var r = redirector(snd, rule);

        assertTrue(r.handleFocusVolumeRequest("steam.exe", 0.6f));
        assertEquals(1, target.calls);
        assertEquals(1, snd.focusCalls, "includeSource on → also apply OS volume to the source");
        assertEquals(0.6f, snd.focusVolume, 0.0001f);
    }

    @Test
    void leavesNonMatchingAppsToTheRestOfTheChain() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), true);
        var r = redirector(snd, rule);

        assertFalse(r.handleFocusVolumeRequest("chrome.exe", 0.5f), "an unmatched app must not be claimed");
        assertFalse(r.controlsFocusApp("chrome.exe"));
        assertEquals(0, target.calls);
        assertEquals(0, snd.focusCalls);
        assertNull(target.received);
    }

    @Test
    void controlsFocusAppReflectsMatchWithoutSideEffects() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), false);
        var r = redirector(snd, rule);

        assertTrue(r.controlsFocusApp("C:\\x\\STEAM.EXE"), "match is case-insensitive on the basename");
        assertEquals(0, target.calls, "controlsFocusApp must be side-effect-free");
    }
}
