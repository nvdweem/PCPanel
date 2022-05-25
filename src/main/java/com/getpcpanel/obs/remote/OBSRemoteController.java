package com.getpcpanel.obs.remote;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import com.getpcpanel.obs.remote.callbacks.Callback;
import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.getpcpanel.obs.remote.communication.response.GetSceneListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourceTypesListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourcesListResponse;

import lombok.extern.log4j.Log4j2;

/**
 * Altered version for https://github.com/obs-websocket-community-projects/obs-websocket-java
 * Seems to have changes for getting sources with audio (GetSourceTypesList) and a different
 * WebSocketClient package that has an isOpen method.
 */
@Log4j2
public class OBSRemoteController {
    private final String address;
    private final OBSCommunicator communicator;
    private WebSocketClient client;
    private boolean failed;

    public OBSRemoteController(String address, String password, boolean autoConnect) {
        this.address = address;
        communicator = new OBSCommunicator(StringUtils.trimToNull(password));
        if (autoConnect)
            connect();
    }

    public OBSRemoteController(String address, String password) {
        this(address, password, true);
    }

    public OBSRemoteController(String address, String port, String password) {
        this("ws://" + StringUtils.firstNonBlank(address, "localhost") + ":" + StringUtils.firstNonBlank(port, "4444"), password, true);
    }

    public void connect() {
        try {
            client = new WebSocketClient(new URI(address), new Draft_6455()) {
                @Override
                public void onMessage(String message) {
                    communicator.onMessage(message);
                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    communicator.onConnect(getThis());
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    failed = true;
                }

                @Override
                public void onError(Exception ex) {
                    failed = true;
                }
            };
            client.connectBlocking();
        } catch (Exception e) {
            failed = true;
            log.error("Failed to start WebSocketClient", e);
        }
    }

    public boolean isConnected() {
        return client.isOpen();
    }

    public void disconnect() {
        try {
            client.closeBlocking();
        } catch (InterruptedException e) {
            log.error("Unable to disconnect from OBS", e);
        }
    }

    private OBSRemoteController getThis() {
        return this;
    }

    protected void sendMessage(String msg) {
        client.send(msg);
    }

    public boolean isFailed() {
        return failed;
    }

    public void getScenes(Callback<GetSceneListResponse> callback) {
        communicator.getScenes(callback);
    }

    public void getSourceTypes(Callback<GetSourceTypesListResponse> callback) {
        communicator.getSourceTypes(callback);
    }

    public void getSources(Callback<GetSourcesListResponse> callback) {
        communicator.getSources(callback);
    }

    public void setCurrentScene(String szene, Callback<BaseResponse> callback) {
        communicator.setCurrentScene(szene, callback);
    }

    public void setVolume(String source, double volume, Callback<BaseResponse> callback) {
        communicator.setVolume(source, volume, callback);
    }

    public void setMute(String source, boolean mute, Callback<BaseResponse> callback) {
        communicator.setMute(source, mute, callback);
    }

    public void toggleMute(String source, Callback<BaseResponse> callback) {
        communicator.toggleMute(source, callback);
    }
}
