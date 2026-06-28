package com.getpcpanel.device.provider.pcpanel;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceType;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.event.Event;

public class PCPanelRGBDevice extends Device {
    private final int[] knobRotations = new int[DeviceType.PCPANEL_RGB.getAnalogCount()];

    public PCPanelRGBDevice(InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter,
            IconService iconService, OverrideColorService overrideColorService, Event<Object> eventBus, DeviceSave deviceSave, String serialNum, DeviceDescriptor descriptor) {
        super(saveService, outputInterpreter, iconService, eventBus, serialNum, deviceSave, descriptor);
    }

    @Override public DeviceType deviceType() { return DeviceType.PCPANEL_RGB; }
    @Override public void setKnobRotation(int knob, int rotation) { knobRotations[knob] = rotation; markKnobSeen(knob); }
    @Override public int getKnobRotation(int knob) { return knobRotations[knob]; }
    @Override public void setButtonPressed(int button, boolean pressed) {}
}
