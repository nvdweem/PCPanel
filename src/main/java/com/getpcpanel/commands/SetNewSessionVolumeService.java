package com.getpcpanel.commands;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.SndCtrl;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

/**
 * Triggers a volume change when a new audio session is started and that session is controlled by the panel.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SetNewSessionVolumeService {
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceHolder devices;
    private final SndCtrl sndCtrl;
    private final SaveService saveService;

    @Async
    @EventListener
    public void onNewAudioSession(AudioSessionEvent event) {
        if (event.eventType() != AudioSessionEvent.Type.ADDED) {
            return;
        }
        record DeviceAndDial(String id, int dial) {
        }

        EntryStream.of(saveService.get().getDevices())
                   .mapValues(DeviceSave::getCurrentProfile)
                   .flatMapKeyValue((id, profile) -> EntryStream.of(profile.getDialData()).mapKeys(d -> new DeviceAndDial(id, d)))
                   .mapToEntry(Map.Entry::getKey, Map.Entry::getValue)
                   .selectValues(CommandVolumeProcess.class)
                   .filterValues(c -> isProcessAndDevice(event, c))
                   .forKeyValue((idAndDial, cmd) -> {
                       var device = devices.getDevice(idAndDial.id);
                       if (device != null) {
                           var current = Util.map(device.getKnobRotation(idAndDial.dial), 0, 100, 0, 255);
                           eventPublisher.publishEvent(new DeviceCommunicationHandler.KnobRotateEvent(idAndDial.id, idAndDial.dial, current));
                       }
                   });
    }

    private boolean isProcessAndDevice(AudioSessionEvent event, CommandVolumeProcess c) {
        var session = event.session();
        if (!c.getProcessName().contains(session.executable().getName())) {
            return false;
        }
        var device = session.device();
        return StringUtils.equals("*", c.getDevice())
                || (StringUtils.isBlank(c.getDevice()) && StringUtils.equals(sndCtrl.defaultDeviceOnEmpty(c.getDevice()), device.id()));
    }
}
