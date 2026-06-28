package com.getpcpanel.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;

/**
 * The decision logic of the focus-volume override service (issue #49): a focused source app redirects the
 * dial to the rule's targets, matches the exe basename case-insensitively (the OS reports a full path),
 * passes the originating device through to the targets (so device-scoped targets like brightness resolve),
 * controls process targets on every render device (apps often don't play on the default one — e.g. Steam),
 * and with {@code includeSource} also drives the source app itself.
 */
class FocusVolumeOverrideServiceTest {
    /** A target Command that records the dial value + device it receives (no CdiHelper → unit-testable). */
    private static final class RecordingTarget extends Command implements DialAction {
        private Float received;
        private String device;
        private int calls;

        @Override
        public void execute(DialActionParameters context) {
            received = context.dial().getValue(this, 0, 1);
            device = context.device();
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

    /** Records setProcessVolume calls (the only ISndCtrl path the service drives directly). */
    private static final class RecordingSnd implements ISndCtrl {
        private record Call(String fileName, String device, float volume) {}

        private final java.util.List<Call> processVolumeCalls = new java.util.ArrayList<>();

        @Override public void setProcessVolume(String fileName, String device, float volume) {
            processVolumeCalls.add(new Call(fileName, device, volume));
        }

        @Override public Map<String, AudioDevice> getDevicesMap() { return Map.of(); }
        @Override public Collection<AudioDevice> devices() { return List.of(); }
        @Override public Collection<AudioSession> getAllSessions() { return List.of(); }
        @Override public AudioDevice getDevice(String id) { return null; }
        @Override public void setDeviceVolume(String deviceId, float volume) {}
        @Override public void muteDevice(String deviceId, MuteType mute) {}
        @Override public void setDefaultDevice(String deviceId) {}
        @Override public void setFocusVolume(float volume) {}
        @Override public void muteProcesses(Set<String> fileName, MuteType mute) {}
        @Override public String getFocusApplication() { return null; }
        @Override public List<RunningApplication> getRunningApplications() { return List.of(); }
        @Override public String defaultDeviceOnEmpty(String deviceId) { return deviceId; }
        @Override public String defaultPlayer() { return null; }
        @Override public String defaultRecorder() { return null; }
    }

    private static FocusVolumeOverrideService service(RecordingSnd snd, FocusVolumeOverride... rules) {
        var save = new Save();
        save.setFocusVolumeOverrides(List.of(rules));
        var s = new FocusVolumeOverrideService();
        s.sndCtrl = snd;
        s.saveService = new SaveService() {
            @Override
            public Save get() {
                return save;
            }
        };
        return s;
    }

    @Test
    void redirectsFocusedSourceToTargets_andClaimsTheRequest() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), false);
        var s = service(snd, rule);

        // The OS reports the full path; matching is by exe basename, case-insensitive.
        var handled = s.handle("C:\\Program Files (x86)\\Steam\\Steam.exe", 0.4f, "PCP-123");

        assertTrue(handled, "a matching rule fully owns the request");
        assertEquals(1, target.calls, "the target must be driven");
        assertEquals(0.4f, target.received, 0.01f, "the target receives the dial value");
        assertEquals("PCP-123", target.device, "the originating device flows through to the target");
        assertTrue(snd.processVolumeCalls.isEmpty(), "includeSource off → the source itself is left untouched");
    }

    @Test
    void includeSourceDrivesTheSourceOnEveryRenderDevice() {
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(), true);
        var s = service(snd, rule);

        assertTrue(s.handle("C:\\x\\Steam.exe", 0.6f, "PCP-123"));
        assertEquals(1, snd.processVolumeCalls.size(), "includeSource on → the source app is driven");
        var call = snd.processVolumeCalls.get(0);
        assertEquals("Steam.exe", call.fileName());
        assertEquals("*", call.device(), "across all render devices, not just the default");
        assertEquals(0.6f, call.volume(), 0.0001f);
    }

    @Test
    void processTargetsControlEveryRenderDevice() {
        // A blank-device App-volume target (the override editor never offers a device picker) is rebuilt to
        // target all render devices, so an app that plays on a non-default device is still controlled.
        var blank = new CommandVolumeProcess(List.of("steam.exe"), "", false, DialCommandParams.DEFAULT);
        var rebuilt = FocusVolumeOverrideService.allRenderDevicesIfUnset(blank);
        assertEquals("*", ((CommandVolumeProcess) rebuilt).getDevice());

        // A deliberately chosen device is preserved.
        var explicit = new CommandVolumeProcess(List.of("steam.exe"), "dev-123", false, DialCommandParams.DEFAULT);
        assertEquals("dev-123", ((CommandVolumeProcess) FocusVolumeOverrideService.allRenderDevicesIfUnset(explicit)).getDevice());
    }

    @Test
    void leavesNonMatchingAppsAlone() {
        var target = new RecordingTarget();
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), true);
        var s = service(snd, rule);

        assertFalse(s.handle("chrome.exe", 0.5f, "PCP-123"), "an unmatched app is not handled");
        assertFalse(s.controls("chrome.exe"));
        assertEquals(0, target.calls);
        assertTrue(snd.processVolumeCalls.isEmpty());
        assertNull(target.received);
    }

    @Test
    void controlsReflectsMatchWithoutSideEffects() {
        var snd = new RecordingSnd();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(new RecordingTarget())), false);
        var s = service(snd, rule);

        assertTrue(s.controls("C:\\x\\STEAM.EXE"), "match is case-insensitive on the basename");
        assertTrue(snd.processVolumeCalls.isEmpty(), "controls() must be side-effect-free");
    }
}
