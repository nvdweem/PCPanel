package dev.niels.elgato.controlcenter.impl;

import java.util.List;
import java.util.concurrent.CompletionStage;

import dev.niels.elgato.controlcenter.impl.model.ControlCenterApplicationInfo;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDevice;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDeviceConfiguration;

public interface IControlCenterRpcCommands {

    CompletionStage<ControlCenterApplicationInfo> getApplicationInfo();

    CompletionStage<List<ControlCenterDevice>> getDevices();

    CompletionStage<ControlCenterDeviceConfiguration> getDeviceConfiguration(GetDeviceConfigurationParam param);

    void setDeviceConfiguration(ControlCenterDeviceConfiguration newConfig);

    record GetDeviceConfigurationParam(String deviceID) {
    }
}
