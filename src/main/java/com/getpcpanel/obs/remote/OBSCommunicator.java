package com.getpcpanel.obs.remote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.getpcpanel.Json;
import com.getpcpanel.obs.remote.callbacks.Callback;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.getpcpanel.obs.remote.communication.request.AuthenticateRequest;
import com.getpcpanel.obs.remote.communication.request.GetAuthRequiredRequest;
import com.getpcpanel.obs.remote.communication.request.GetSceneListRequest;
import com.getpcpanel.obs.remote.communication.request.GetSourceTypesListRequest;
import com.getpcpanel.obs.remote.communication.request.GetSourcesListRequest;
import com.getpcpanel.obs.remote.communication.request.GetVersionRequest;
import com.getpcpanel.obs.remote.communication.request.SetCurrentSceneRequest;
import com.getpcpanel.obs.remote.communication.request.SetMuteRequest;
import com.getpcpanel.obs.remote.communication.request.SetVolumeRequest;
import com.getpcpanel.obs.remote.communication.request.ToggleMuteRequest;
import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.getpcpanel.obs.remote.communication.response.GetAuthRequiredResponse;
import com.getpcpanel.obs.remote.communication.response.GetSceneListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourceTypesListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourcesListResponse;
import com.getpcpanel.obs.remote.communication.response.GetVersionResponse;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class OBSCommunicator {
    private final String password;
    public final Map<String, RequestType> messageTypes = new HashMap<>();
    private final Map<Class<?>, Callback> callbacks = new HashMap<>();
    private OBSRemoteController com;

    public OBSCommunicator(String password) {
        this.password = password;
    }

    public void onConnect(OBSRemoteController com) {
        this.com = com;
        sendMessage(Json.write(new GetVersionRequest(this)));
    }

    public void onMessage(String msg) {
        if (msg == null) {
            log.debug("Ignored empty message");
            return;
        }
        log.debug(msg);
        try {
            var preResponse = Json.read(msg, Map.class).get("message-id");
            if (preResponse != null) {
                var type = messageTypes.remove(preResponse.toString());
                var responseBase = Json.read(msg, type.getResponse());
                processIncomingResponse(responseBase, type.getResponse());
            }
        } catch (Throwable t) {
            log.error("Failed to process message from websocket.", t);
        }
    }

    private void processIncomingResponse(BaseResponse baseResponse, Class<?> type) {
        GetAuthRequiredResponse authRequiredResponse;
        switch (type.getSimpleName()) {
            case "AuthenticateResponse" -> {
                return;
            }
            case "GetAuthRequiredResponse" -> {
                authRequiredResponse = (GetAuthRequiredResponse) baseResponse;
                if (authRequiredResponse.isAuthRequired()) {
                    log.info("Authentication is required.");
                    authenticateWithServer(authRequiredResponse.getChallenge(), authRequiredResponse.getSalt());
                } else {
                    log.info("Authentication is not required. You're ready to go!");
                }
                return;
            }
            case "GetVersionResponse" -> {
                var versionInfo = (GetVersionResponse) baseResponse;
                log.info("Connected to OBS. Websocket Version: {}}, Studio Version: {}}\n", versionInfo.getObsWebsocketVersion(), versionInfo.getObsStudioVersion());
                sendMessage(Json.write(new GetAuthRequiredRequest(this)));
                return;
            }
        }
        if (!callbacks.containsKey(type)) {
            log.info("Invalid type received: {}", type.getName());
            return;
        }
        try {
            callbacks.get(type).run(baseResponse);
        } catch (Throwable t) {
            log.error("Failed to execute callback for response: {}", type, t);
        }
    }

    private void authenticateWithServer(String challenge, String salt) {
        if (password == null) {
            log.error("Authentication required by server but no password set by client");
            return;
        }
        var authResponse = generateAuthenticationResponseString(challenge, salt);
        if (authResponse == null)
            return;
        sendMessage(Json.write(new AuthenticateRequest(this, authResponse)));
    }

    private String generateAuthenticationResponseString(String challenge, String salt) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to perform password authentication with server", e);
            return null;
        }
        var secretString = password + salt;
        var secretHash = digest.digest(secretString.getBytes(StandardCharsets.UTF_8));
        var encodedSecret = Base64.getEncoder().encodeToString(secretHash);
        var authResponseString = encodedSecret + challenge;
        var authResponseHash = digest.digest(authResponseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(authResponseHash);
    }

    public void getScenes(Callback<GetSceneListResponse> callback) {
        sendMessage(Json.write(new GetSceneListRequest(this)));
        callbacks.put(GetSceneListResponse.class, callback);
    }

    public void getSourceTypes(Callback<GetSourceTypesListResponse> callback) {
        sendMessage(Json.write(new GetSourceTypesListRequest(this)));
        callbacks.put(GetSourceTypesListResponse.class, callback);
    }

    public void getSources(Callback<GetSourcesListResponse> callback) {
        sendMessage(Json.write(new GetSourcesListRequest(this)));
        callbacks.put(GetSourcesListResponse.class, callback);
    }

    public void setCurrentScene(String scene, Callback<BaseResponse> callback) {
        sendMessage(Json.write(new SetCurrentSceneRequest(this, scene)));
        callbacks.put(BaseResponse.class, callback);
    }

    public void setVolume(String source, double volume, Callback<BaseResponse> callback) {
        var request = new SetVolumeRequest(this, source, volume);
        sendMessage(Json.write(request));
        callbacks.put(BaseResponse.class, callback);
    }

    public void setMute(String source, boolean mute, Callback<BaseResponse> callback) {
        var request = new SetMuteRequest(this, source, mute);
        sendMessage(Json.write(request));
        callbacks.put(BaseResponse.class, callback);
    }

    public void toggleMute(String source, Callback<BaseResponse> callback) {
        var request = new ToggleMuteRequest(this, source);
        sendMessage(Json.write(request));
        callbacks.put(BaseResponse.class, callback);
    }

    private void sendMessage(String msg) {
        com.sendMessage(msg);
    }
}
