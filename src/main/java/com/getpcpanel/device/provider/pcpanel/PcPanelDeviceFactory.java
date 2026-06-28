package com.getpcpanel.device.provider.pcpanel;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Builds the concrete PCPanel device model (RGB / Mini / Pro) for a connected PCPanel device. The
 * subclass is chosen from the descriptor's device-kind (the PCPanel {@link DeviceType}); the subclasses
 * are otherwise identical and exist only for the per-model {@link Device#deviceType()} value and array
 * size. This is PCPanel-provider-internal construction — non-PCPanel providers use
 * {@link com.getpcpanel.device.GenericDeviceFactory}.
 */
@ApplicationScoped
public class PcPanelDeviceFactory {
    @Inject InputInterpreter inputInterpreter;
    @Inject SaveService saveService;
    @Inject OutputInterpreter outputInterpreter;
    @Inject IconService iconService;
    @Inject OverrideColorService overrideColorService;
    @Inject Event<Object> eventBus;

    public Device build(String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        var type = DeviceType.valueOf(descriptor.deviceKindId());
        return switch (type) {
            case PCPANEL_RGB -> new PCPanelRGBDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, deviceSave, serialNum, descriptor);
            case PCPANEL_MINI -> new PCPanelMiniDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, serialNum, deviceSave, descriptor);
            case PCPANEL_PRO -> new PCPanelProDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, serialNum, deviceSave, descriptor);
        };
    }
}
