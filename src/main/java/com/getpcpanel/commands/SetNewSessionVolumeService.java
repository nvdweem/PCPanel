package com.getpcpanel.commands;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.windows.WindowsAudioSession;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.util.Util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/**
 * Triggers a volume change when a new audio session is started and that session is controlled by the panel.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SetNewSessionVolumeService {
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceHolder devices;
    private final ISndCtrl sndCtrl;

    @EventListener
    public void onNewAudioSession(AudioSessionEvent event) {
        if (event.eventType() != EventType.ADDED) {
            return;
        }
        record DeviceAndDial(String id, int dial) {
        }

        StreamEx.of(devices.all())
                .mapToEntry(Device::getSerialNumber).invert()
                .mapValues(Device::currentProfile)
                .flatMapKeyValue((id, profile) -> EntryStream.of(profile.getDialData()).mapKeys(d -> new DeviceAndDial(id, d)))
                .mapToEntry(Map.Entry::getKey, Map.Entry::getValue)
                .selectValues(CommandVolumeProcess.class)
                .filterValues(c -> isProcessAndDevice(event, c))
                .forKeyValue((idAndDial, cmd) -> {
                    var device = devices.getDevice(idAndDial.id);
                    if (device != null) {
                        var current = Util.map(device.getKnobRotation(idAndDial.dial), 0, 100, 0, 255);
                        eventPublisher.publishEvent(new DeviceCommunicationHandler.KnobRotateEvent(idAndDial.id, idAndDial.dial, current, false));
                    }
                });
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
