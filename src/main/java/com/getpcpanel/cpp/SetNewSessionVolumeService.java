package com.getpcpanel.cpp;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.AbstractNewXVolumeService;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.windows.WindowsAudioSession;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

/**
 * Triggers a volume change when a new audio session is started and that session is controlled by the panel.
 */
@Log4j2
@ApplicationScoped
public class SetNewSessionVolumeService extends AbstractNewXVolumeService {
    private final ISndCtrl sndCtrl;
    private final SaveService save;

    public SetNewSessionVolumeService(DeviceHolder devices, Event<Object> eventPublisher, @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ISndCtrl sndCtrl, SaveService save) {
        super(devices, eventPublisher);
        this.sndCtrl = sndCtrl;
        this.save = save;
    }

    public void onNewAudioSession(@Observes AudioSessionEvent event) {
        if (event.eventType() == EventType.ADDED || (save.get().isForceVolume() && event.eventType() == EventType.CHANGED)) {
            triggerCommandsOf(CommandVolumeProcess.class, s -> s.filterValues(c -> isProcessAndDevice(event, c)));
        }
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
