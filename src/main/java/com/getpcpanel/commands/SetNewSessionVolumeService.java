package com.getpcpanel.commands;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.windows.WindowsAudioSession;
import com.getpcpanel.hid.DeviceHolder;

import lombok.extern.log4j.Log4j2;

/**
 * Triggers a volume change when a new audio session is started and that session is controlled by the panel.
 */
@Log4j2
@Service
public class SetNewSessionVolumeService extends AbstractNewXVolumeService {
    private final ISndCtrl sndCtrl;

    public SetNewSessionVolumeService(DeviceHolder devices, ApplicationEventPublisher eventPublisher, @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ISndCtrl sndCtrl) {
        super(devices, eventPublisher);
        this.sndCtrl = sndCtrl;
    }

    @EventListener
    public void onNewAudioSession(AudioSessionEvent event) {
        if (event.eventType() != EventType.ADDED) {
            return;
        }
        triggerCommandsOf(CommandVolumeProcess.class, s -> s.filterValues(c -> isProcessAndDevice(event, c)));
    }

    private boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (!c.getProcessName().contains(session.executable().getName())) {
            return false;
        }

        if (session instanceof WindowsAudioSession wis) {
            var device = wis.device();
            return StringUtils.equals("*", c.getDevice())
                    || (StringUtils.isBlank(c.getDevice()) && StringUtils.equals(sndCtrl.defaultDeviceOnEmpty(c.getDevice()), device.id()));
        }
        return true;
    }
}
