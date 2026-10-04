package com.getpcpanel.integration.volume.platform.windows;

import java.util.Set;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.AppOutputRouter;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.platform.WindowsBuild;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Sets the persisted per-process playback endpoint (what Windows' "App volume and device preferences" sets) for every
 * process of the apps that has an audio session. A null device clears it, so the apps follow the default output.
 */
@Log4j2
@Unremovable
@WindowsBuild
@ApplicationScoped
class WindowsAppOutputRouter implements AppOutputRouter {
    private static final String DEFAULT_OUTPUT = "Default output";

    @Inject SndCtrlWindows sndCtrl;

    @Override
    public @Nullable String route(Set<String> exeNames, @Nullable String deviceId) {
        var device = deviceId == null ? null : sndCtrl.getDevice(deviceId);
        if (deviceId != null && (device == null || !device.isOutput())) {
            log.warn("Cannot send {} to {}: not an output device", exeNames, deviceId);
            return null;
        }
        var pids = StreamEx.of(sndCtrl.getAllSessions())
                           .filter(s -> s.executable() != null && !s.isSystemSounds() && AppOutputRouter.namesApp(exeNames, s.executable().getPath()))
                           .map(AudioSession::pid)
                           .toSet();
        if (pids.isEmpty()) {
            return null;
        }
        pids.forEach(pid -> sndCtrl.setDeviceForProcess(pid, DataFlow.dfRender, deviceId));
        return device != null ? device.name() : defaultOutputName();
    }

    private String defaultOutputName() {
        var player = sndCtrl.defaultPlayer();
        var device = player == null ? null : sndCtrl.getDevice(player);
        return device == null ? DEFAULT_OUTPUT : device.name();
    }
}
