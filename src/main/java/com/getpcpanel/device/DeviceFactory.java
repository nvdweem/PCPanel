package com.getpcpanel.device;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.ui.FxHelper;

import lombok.RequiredArgsConstructor;

@Service
@Configuration
@RequiredArgsConstructor
public class DeviceFactory {
    private final FxHelper fxHelper;
    private final InputInterpreter inputInterpreter;
    private final SaveService saveService;
    private final OutputInterpreter outputInterpreter;
    private final IconService iconService;

    public PCPanelRGBUI buildRgb(String serialNum, DeviceSave deviceSave) {
        return new PCPanelRGBUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, deviceSave, serialNum);
    }

    public PCPanelMiniUI buildMini(String serialNum, DeviceSave deviceSave) {
        return new PCPanelMiniUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, serialNum, deviceSave);
    }

    public PCPanelProUI buildPro(String serialNum, DeviceSave deviceSave) {
        return new PCPanelProUI(fxHelper, inputInterpreter, saveService, outputInterpreter, iconService, serialNum, deviceSave);
    }
}
