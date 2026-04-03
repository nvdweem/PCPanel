package com.getpcpanel.device;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import lombok.RequiredArgsConstructor;

@ApplicationScoped
public class DeviceFactory {
    @Inject InputInterpreter inputInterpreter;
    @Inject SaveService saveService;
    @Inject OutputInterpreter outputInterpreter;
    @Inject IconService iconService;
    @Inject OverrideColorService overrideColorService;

    public Device buildRgb(String serialNum, DeviceSave deviceSave) {
        return new PCPanelRGBDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, deviceSave, serialNum);
    }

    public Device buildMini(String serialNum, DeviceSave deviceSave) {
        return new PCPanelMiniDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, serialNum, deviceSave);
    }

    public Device buildPro(String serialNum, DeviceSave deviceSave) {
        return new PCPanelProDevice(inputInterpreter, saveService, outputInterpreter, iconService, overrideColorService, serialNum, deviceSave);
    }
}


import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import lombok.RequiredArgsConstructor;

@ApplicationScoped

public class DeviceFactory {
    @Inject
    FxHelper fxHelper;
    @Inject
    InputInterpreter inputInterpreter;
    @Inject
    SaveService saveService;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    IconService iconService;
    @Inject
    Event<Object> eventBus;
    @Inject
    OverrideColorService overrideColorService;

    public PCPanelRGBUI buildRgb(String serialNum, DeviceSave deviceSave) {
        return new PCPanelRGBUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, eventPublisher, overrideColorService, deviceSave, serialNum);
    }

    public PCPanelMiniUI buildMini(String serialNum, DeviceSave deviceSave) {
        return new PCPanelMiniUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, eventPublisher, overrideColorService, serialNum, deviceSave);
    }

    public PCPanelProUI buildPro(String serialNum, DeviceSave deviceSave) {
        return new PCPanelProUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, eventPublisher, overrideColorService, serialNum, deviceSave);
    }
}
