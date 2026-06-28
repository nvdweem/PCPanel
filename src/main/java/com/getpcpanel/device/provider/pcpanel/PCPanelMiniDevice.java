package com.getpcpanel.device.provider.pcpanel;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.provider.pcpanel.DeviceType;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.event.Event;

public class PCPanelMiniDevice extends Device {
    private final int[] knobRotations = new int[DeviceType.PCPANEL_MINI.getAnalogCount()];

    public PCPanelMiniDevice(InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter,
            IconService iconService, OverrideColorService overrideColorService, Event<Object> eventBus, String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        super(saveService, outputInterpreter, iconService, eventBus, serialNum, deviceSave, descriptor);
    }

    @Override public DeviceType deviceType() { return DeviceType.PCPANEL_MINI; }
    @Override public void setKnobRotation(int knob, int rotation) { knobRotations[knob] = rotation; markKnobSeen(knob); }
    @Override public int getKnobRotation(int knob) { return knobRotations[knob]; }
    @Override public void setButtonPressed(int button, boolean pressed) {}
}
