package dev.niels.elgato.controlcenter.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.niels.elgato.controlcenter.impl.IControlCenterRpcCommands.GetDeviceConfigurationParam;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterApplicationInfo;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDevice;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDeviceConfiguration;
import dev.niels.elgato.jsonrpc.JsonRpcSender;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@RequiredArgsConstructor
class ControlCenterService implements IControlCenterService {
    private final ControlCenterClientImpl client;
    @Setter(AccessLevel.PACKAGE) private IControlCenterRpcCommands commands;
    @Getter private final Map<String, ControlCenterDeviceConfiguration> deviceConfigurations = new ConcurrentHashMap<>();
    @Getter private final Map<String, ControlCenterDevice> devices = new ConcurrentHashMap<>();

    @Override
    public void _onConnect(JsonRpcSender sender) {
        commands.getApplicationInfo()
                .thenAccept(res -> {
                    ensureCorrectVersion(res);
                    updateAllDevices();
                    log.debug("Connected to Control Center, getting info");
                });
    }

    private void updateAllDevices() {
        commands.getDevices().thenAccept(newDevices -> {
            synchronized (devices) {
                devices.clear();
                StreamEx.of(newDevices).mapToEntry(ControlCenterDevice::deviceID).invert()
                        .into(devices);
            }

            client.setInitialized();
        });
    }

    private void ensureCorrectVersion(ControlCenterApplicationInfo res) {
        log.info("Connected websocket, control center info: {}", res);
        var correctAppId = "eccw".equalsIgnoreCase(res.appID());
        var correctAppName = "Elgato Control Center".equalsIgnoreCase(res.name());
        if (!correctAppId || !correctAppName) {
            throw new IllegalStateException("Expected appId ewl and appName Elgato Control Center, got " + res);
        }
    }

    @Override
    public void deviceConfigurationChanged(ControlCenterDeviceConfiguration configuration) {
        deviceConfigurations.put(configuration.deviceID(), configuration);
        client.trigger(l -> l.deviceConfigurationChanged(configuration.deviceID()));
    }

    @Override
    public void deviceRemoved(ControlCenterDevice device) {
        devices.remove(device.deviceID());
        client.trigger(l -> l.deviceRemoved(device.deviceID()));
    }

    @Override
    public void deviceAdded(ControlCenterDevice device) {
        devices.put(device.deviceID(), device);
        commands.getDeviceConfiguration(new GetDeviceConfigurationParam(device.deviceID()))
                .thenAccept(this::deviceConfigurationChanged);
    }
}
