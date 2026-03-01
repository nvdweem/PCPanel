package dev.niels.elgato.controlcenter.impl;

import static lombok.AccessLevel.PROTECTED;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.niels.elgato.controlcenter.IControlCenterClient;
import dev.niels.elgato.controlcenter.IControlCenterEventListener;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDevice;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterDeviceConfiguration;
import dev.niels.elgato.controlcenter.impl.model.ControlCenterLights;
import dev.niels.elgato.jsonrpc.JsonRpcClient;
import dev.niels.elgato.shared.ClientImpl;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class ControlCenterClientImpl extends ClientImpl<IControlCenterEventListener> implements IControlCenterClient, AutoCloseable {
    private final ControlCenterService service;
    @Getter(PROTECTED) private final JsonRpcClient jsonRpc;
    private final IControlCenterRpcCommands commands;

    protected ControlCenterClientImpl(boolean autoConnect) {
        service = new ControlCenterService(this);
        jsonRpc = new JsonRpcClient("ws://127.0.0.1:1804", false, this, service);
        commands = jsonRpc.buildCommands(IControlCenterRpcCommands.class);
        service.setCommands(commands);
        if (autoConnect) {
            jsonRpc.connect();
        }
    }

    public Map<String, ControlCenterDevice> getDevices() {
        return new HashMap<>(service.getDevices());
    }

    public ControlCenterDeviceConfiguration getDeviceConfig(String deviceId) {
        return service.getDeviceConfigurations().getOrDefault(deviceId, new ControlCenterDeviceConfiguration(deviceId, ControlCenterLights.EMPTY));
    }

    public void setDeviceConfiguration(ControlCenterDeviceConfiguration newConfig) {
        commands.setDeviceConfiguration(newConfig);
    }

    @Override
    public boolean isConnected() {
        return jsonRpc.isConnected();
    }

    @Override
    public void ping() {
        jsonRpc.ping();
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        return jsonRpc.reconnect();
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return jsonRpc.disconnect();
    }

    @Override
    public void close() {
        jsonRpc.close();
    }
}
