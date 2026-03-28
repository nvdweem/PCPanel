package com.getpcpanel.device;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class DeviceFactory {
    private final FxHelper fxHelper;
    private final InputInterpreter inputInterpreter;
    private final SaveService saveService;
    private final OutputInterpreter outputInterpreter;
    private final IconService iconService;
    private final Event<Object> eventPublisher;
    private final OverrideColorService overrideColorService;

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
