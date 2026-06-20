package com.getpcpanel.device;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

@ApplicationScoped
public class DeviceFactory {
    @Inject InputInterpreter inputInterpreter;
    @Inject SaveService saveService;
    @Inject OutputInterpreter outputInterpreter;
    @Inject IconService iconService;
    @Inject OverrideColorService overrideColorService;
    @Inject Event<Object> eventBus;

    /**
     * Builds a PCPanel device from its capability descriptor. The concrete subclass is chosen from
     * the descriptor's device-kind (the PCPanel {@link DeviceType}); the subclasses are otherwise
     * identical and exist only for the per-model {@link Device#deviceType()} value and array size.
     */
    public Device build(String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        var type = DeviceType.valueOf(descriptor.deviceKindId());
        return switch (type) {
            case PCPANEL_RGB -> new PCPanelRGBDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, deviceSave, serialNum, descriptor);
            case PCPANEL_MINI -> new PCPanelMiniDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, serialNum, deviceSave, descriptor);
            case PCPANEL_PRO -> new PCPanelProDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, eventBus, serialNum, deviceSave, descriptor);
        };
    }
}
