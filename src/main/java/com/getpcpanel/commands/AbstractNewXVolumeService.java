package com.getpcpanel.commands;

import java.util.Map;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.util.Util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/**
 * Parent class for services that trigger when a process starts or another tool connects.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public abstract class AbstractNewXVolumeService {
    private final DeviceHolder devices;
    private final ApplicationEventPublisher eventPublisher;

    protected <T extends Command> void triggerCommandsOf(Class<T> clazz, Function<EntryStream<DeviceAndDial, T>, EntryStream<DeviceAndDial, T>> chain) {
        StreamEx.of(devices.all())
                .mapToEntry(Device::getSerialNumber).invert()
                .mapValues(Device::currentProfile)
                .flatMapKeyValue((id, profile) -> EntryStream.of(profile.getDialData()).mapKeys(d -> new DeviceAndDial(id, d)))
                .mapToEntry(Map.Entry::getKey, Map.Entry::getValue)
                .flatMapValues(d -> Commands.cmds(d).stream())
                .selectValues(clazz)
                .chain(chain)
                .forKeyValue((idAndDial, cmd) -> devices.getDevice(idAndDial.id()).ifPresent(device -> {
                    var current = Util.map(device.getKnobRotation(idAndDial.dial()), 0, 100, 0, 255);
                    eventPublisher.publishEvent(new DeviceCommunicationHandler.KnobRotateEvent(idAndDial.id(), idAndDial.dial(), current, false));
                }));
    }

    protected record DeviceAndDial(String id, int dial) {
    }
}
