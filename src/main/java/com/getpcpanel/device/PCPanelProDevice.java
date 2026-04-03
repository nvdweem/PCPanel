package com.getpcpanel.device;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

public class PCPanelProDevice extends Device {
    private final int[] knobRotations = new int[DeviceType.PCPANEL_PRO.getAnalogCount()];

    public PCPanelProDevice(InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter,
            IconService iconService, OverrideColorService overrideColorService, String serialNum, DeviceSave deviceSave) {
        super(saveService, outputInterpreter, iconService, serialNum, deviceSave);
    }

    @Override public DeviceType deviceType() { return DeviceType.PCPANEL_PRO; }
    @Override public void setKnobRotation(int knob, int rotation) { knobRotations[knob] = rotation; }
    @Override public int getKnobRotation(int knob) { return knobRotations[knob]; }
    @Override public void setButtonPressed(int button, boolean pressed) {}
    @Override public void closeDialogs() {}
    @Override public void showLightingConfigToUI(LightingConfig config) {}
}
