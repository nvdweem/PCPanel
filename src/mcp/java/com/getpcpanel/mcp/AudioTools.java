package com.getpcpanel.mcp;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.volume.VolumeCoordinatorService;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import one.util.streamex.StreamEx;

/**
 * Reads current OS audio state (per-device + per-process volume/mute, defaults, focused app) from
 * {@link ISndCtrl}. Combined with the simulation tools this closes the end-to-end loop: drive a
 * control to 50% then assert the bound process volume actually moved - a real end-to-end test that
 * needed hardware before.
 */
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class AudioTools {
    @Inject Instance<ISndCtrl> sndCtrl;
    @Inject VolumeCoordinatorService volumeCoordinator;

    @Tool(description = "Current OS audio state: output/input devices and per-process sessions with "
            + "their volume (0.0-1.0) and mute, plus the default player/recorder and focused app. "
            + "Optional filter is a case-insensitive substring matched against device/session names. "
            + "available=false when no audio backend is present (wrong OS).")
    public AudioState pcpanel_get_audio_state(
            @ToolArg(required = false, description = "Case-insensitive substring filter on names") String filter) {
        if (!sndCtrl.isResolvable()) {
            return new AudioState(false, null, null, null, List.of(), List.of());
        }
        var snd = sndCtrl.get();
        // AudioDevice / AudioSession use fluent Lombok accessors (see cpp/lombok.config).
        var devices = StreamEx.of(snd.devices())
                              .filter(d -> matches(filter, d.name()))
                              .map(d -> new DeviceVolume(d.id(), d.name(), String.valueOf(d.dataflow()),
                                      d.volume(), d.muted()))
                              .toList();
        var sessions = StreamEx.of(snd.getAllSessions())
                               .filter(s -> matches(filter, s.title())
                                       || matches(filter, s.executable() == null ? null : s.executable().getName()))
                               .map(s -> new SessionVolume(s.pid(),
                                       s.executable() == null ? null : s.executable().getName(),
                                       s.title(), s.volume(), s.muted()))
                               .toList();
        return new AudioState(true, snd.defaultPlayer(), snd.defaultRecorder(), snd.getFocusApplication(),
                devices, sessions);
    }

    @Tool(description = "Inspect where a given focused application's volume would go: returns the "
            + "redirector that claims it (e.g. WaveLinkService) or null when the OS controls it directly. "
            + "Side-effect-free - evaluates the deferral decision without changing any volume. Use it to "
            + "assert focused-app volume defers to Wave Link for a Wave-Link-managed app and hits the OS "
            + "otherwise. Pass the application as the OS focus path (e.g. C:\\\\...\\\\firefox.exe).")
    public FocusVolumeTarget pcpanel_focus_volume_target(
            @ToolArg(description = "Focused application, as ISndCtrl.getFocusApplication() reports it (full exe path)") String application) {
        var handler = volumeCoordinator.focusVolumeTarget(application).orElse(null);
        return new FocusVolumeTarget(application, handler, handler != null, volumeCoordinator.wouldSkipFocusVolume(application));
    }

    private static boolean matches(String filter, String value) {
        return StringUtils.isEmpty(filter) || (value != null && StringUtils.containsIgnoreCase(value, filter));
    }

    public record AudioState(
            boolean available,
            String defaultPlayer,
            String defaultRecorder,
            String focusApplication,
            List<DeviceVolume> devices,
            List<SessionVolume> sessions) {
    }

    public record DeviceVolume(String id, String name, String dataflow, float volume, boolean muted) {
    }

    public record SessionVolume(int pid, String executable, String title, float volume, boolean muted) {
    }

    /** {@code handledBy} = redirector class that claims this app's focus volume, or null when the OS does. */
    public record FocusVolumeTarget(String application, String handledBy, boolean defersToIntegration, boolean skipped) {
    }
}
