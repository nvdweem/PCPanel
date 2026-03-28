package com.getpcpanel.commands;

import java.util.Map.Entry;
import java.util.function.Function;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceHolder;

import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/**
 * Parent class for services that trigger when a process starts or another tool connects.
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractNewXVolumeService {
    private final DeviceHolder devices;
    private final Event<Object> eventPublisher;

    protected AbstractNewXVolumeService() {
        eventPublisher = null;
        devices = null;
    }

    protected <T extends Command> void triggerCommandsOf(Class<T> clazz, Function<EntryStream<DeviceAndDial, T>, EntryStream<DeviceAndDial, T>> chain) {
        StreamEx.of(devices.all())
                .mapToEntry(Device::getSerialNumber).invert()
                .mapValues(Device::currentProfile)
                .flatMapKeyValue((id, profile) -> EntryStream.of(profile.getDialData()).mapKeys(d -> new DeviceAndDial(id, d)))
                .mapToEntry(Entry::getKey, Entry::getValue)
                .flatMapValues(d -> Commands.cmds(d).stream())
                .selectValues(clazz)
                .chain(chain)
                .forKeyValue((idAndDial, cmd) -> devices.getDevice(idAndDial.id()).ifPresent(device -> {
                    var current = device.getKnobRotation(idAndDial.dial());
                    eventPublisher.fire(new KnobRotateEvent(idAndDial.id(), idAndDial.dial(), current, false));
                }));
    }

    protected record DeviceAndDial(String id, int dial) {
    }
}
