package com.getpcpanel.device;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Builds a descriptor-only {@link GenericDevice} (no {@link DeviceType}, no buttons, no lights) for any
 * non-PCPanel provider such as Deej or MIDI. PCPanel devices are built by
 * {@link com.getpcpanel.device.provider.pcpanel.PcPanelDeviceFactory} instead.
 */
@ApplicationScoped
public class GenericDeviceFactory {
    @Inject SaveService saveService;
    @Inject OutputInterpreter outputInterpreter;
    @Inject IconService iconService;
    @Inject Event<Object> eventBus;

    public Device build(String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        return new GenericDevice(saveService, outputInterpreter, iconService, eventBus, serialNum, deviceSave, descriptor);
    }
}
