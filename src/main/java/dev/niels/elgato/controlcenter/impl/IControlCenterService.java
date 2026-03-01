package dev.niels.elgato.controlcenter.impl;

import dev.niels.elgato.controlcenter.impl.model.ControlCenterDevice;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDeviceConfiguration;
import dev.niels.elgato.jsonrpc.JsonRpcService;

@SuppressWarnings("unused") // Called via reflection
interface IControlCenterService extends JsonRpcService {
    void deviceConfigurationChanged(ControlCenterDeviceConfiguration configuration);

    void deviceRemoved(ControlCenterDevice device);

    void deviceAdded(ControlCenterDevice device);
}
