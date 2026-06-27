package com.getpcpanel.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;

/**
 * The decision logic of the focus-volume override service (issue #49): a focused source app redirects the
 * dial to the rule's targets, matches the exe basename case-insensitively (the OS reports a full path),
 * passes the originating device through to the targets (so device-scoped targets like brightness resolve),
 * and only lets the source's own volume flow on when {@code includeSource} is on.
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

    private static FocusVolumeOverrideService service(FocusVolumeOverride... rules) {
        var save = new Save();
        save.setFocusVolumeOverrides(List.of(rules));
        var s = new FocusVolumeOverrideService();
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
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), false);
        var s = service(rule);

        // The OS reports the full path; matching is by exe basename, case-insensitive.
        var fullyHandled = s.handle("C:\\Program Files (x86)\\Steam\\Steam.exe", 0.4f, "PCP-123");

        assertTrue(fullyHandled, "includeSource off → the service fully owns the request");
        assertEquals(1, target.calls, "the target must be driven");
        assertEquals(0.4f, target.received, 0.01f, "the target receives the dial value");
        assertEquals("PCP-123", target.device, "the originating device flows through to the target");
    }

    @Test
    void includeSourceLetsTheSourceVolumeFlowOn() {
        var target = new RecordingTarget();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), true);
        var s = service(rule);

        var fullyHandled = s.handle("steam.exe", 0.6f, "PCP-123");

        assertFalse(fullyHandled, "includeSource on → caller continues so the source's own volume is set too");
        assertEquals(1, target.calls, "the targets still run");
    }

    @Test
    void leavesNonMatchingAppsAlone() {
        var target = new RecordingTarget();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), true);
        var s = service(rule);

        assertFalse(s.handle("chrome.exe", 0.5f, "PCP-123"), "an unmatched app is not handled");
        assertFalse(s.controls("chrome.exe"));
        assertEquals(0, target.calls);
        assertNull(target.received);
    }

    @Test
    void controlsReflectsMatchWithoutSideEffects() {
        var target = new RecordingTarget();
        var rule = new FocusVolumeOverride(List.of("steam.exe"), List.of(new FocusVolumeTarget(target)), false);
        var s = service(rule);

        assertTrue(s.controls("C:\\x\\STEAM.EXE"), "match is case-insensitive on the basename");
        assertEquals(0, target.calls, "controls() must be side-effect-free");
    }
}
