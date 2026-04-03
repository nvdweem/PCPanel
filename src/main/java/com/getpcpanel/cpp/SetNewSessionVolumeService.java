package com.getpcpanel.cpp;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.windows.WindowsAudioSession;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

/**
 * Triggers a volume change when a new audio session is started and that session is controlled by the panel.
 */
@JBossLog
@ApplicationScoped
public class SetNewSessionVolumeService extends AbstractNewXVolumeService {
    @Inject ISndCtrl sndCtrl;
    @Inject SaveService save;

    public void onNewAudioSession(@Observes AudioSessionEvent event) {
        if (event.eventType() == EventType.ADDED || (save.get().isForceVolume() && event.eventType() == EventType.CHANGED)) {
            triggerCommandsOf(CommandVolumeProcess.class, s -> s.filterValues(c -> isProcessAndDevice(event, c)));
        }
    }

    private boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (session.executable() == null) return false;
        if (!c.getProcessName().contains(session.executable().getName())) {
            return false;
        }
        var deviceId = c.getDevice();
        if (StringUtils.isBlank(deviceId) || "*".equals(deviceId)) return true;
        if (session instanceof WindowsAudioSession was) {
            return deviceId.equals(was.device().getId());
        }
        return false;
    }
}
