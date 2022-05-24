package obsremote;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import obsremote.callbacks.Callback;
import obsremote.callbacks.ErrorCallback;
import obsremote.callbacks.StringCallback;
import obsremote.events.EventType;
import obsremote.events.responses.ScenesChangedResponse;
import obsremote.events.responses.SwitchScenesResponse;
import obsremote.events.responses.TransitionBeginResponse;
import obsremote.events.responses.TransitionEndResponse;
import obsremote.objects.throwables.InvalidResponseTypeError;
import obsremote.requests.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OBSCommunicator {
    private final boolean debug;

    private final String password;

    private final CountDownLatch closeLatch;

    public final Map<String, Class<?>> messageTypes = new HashMap<>();

    private final Map<Class<?>, Callback> callbacks = new HashMap<>();

    private OBSRemoteController com;

    private Callback onConnect;

    private Callback onDisconnect;

    private StringCallback onConnectionFailed;

    private ErrorCallback onError;

    private Callback onRecordingStarted;

    private Callback onRecordingStopped;

    private Callback onReplayStarted;

    private Callback onReplayStarting;

    private Callback onReplayStopped;

    private Callback onReplayStopping;

    private Callback onStreamStarted;

    private Callback onStreamStopped;

    private Callback onSwitchScenes;

    private Callback onScenesChanged;

    private Callback onTransitionBegin;

    private Callback onTransitionEnd;

    private GetVersionResponse versionInfo;

    public OBSCommunicator(boolean debug, String password) {
        closeLatch = new CountDownLatch(1);
        this.debug = debug;
        this.password = password;
    }

    public OBSCommunicator(boolean debug) {
        this(debug, null);
    }

    public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(duration, unit);
    }

    public void await() throws InterruptedException {
        closeLatch.await();
    }

    public void onClose(int statusCode, String reason) {
        System.out.printf("Connection closed: %d - %s%n", Integer.valueOf(statusCode), reason);
        closeLatch.countDown();
        try {
            onDisconnect.run(null);
        } catch (Throwable throwable) {
        }
    }

    public void onConnect(OBSRemoteController com) {
        this.com = com;
        try {
            sendMessage(new Gson().toJson(new GetVersionRequest(this)));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void onMessage(String msg) {
        if (msg == null) {
            System.out.println("Ignored empty message");
            return;
        }
        if (debug)
            System.out.println(msg);
        try {
            if (new Gson().fromJson(msg, JsonObject.class).has("message-id")) {
                ResponseBase responseBase = new Gson().fromJson(msg, ResponseBase.class);
                Class type = messageTypes.get(responseBase.getMessageId());
                responseBase = (ResponseBase) new Gson().fromJson(msg, type);
                try {
                    processIncomingResponse(responseBase, type);
                } catch (Throwable t) {
                    System.err.println("Failed to process response '" + type.getSimpleName() + "' from websocket.");
                    t.printStackTrace();
                    runOnError("Failed to process response '" + type.getSimpleName() + "' from websocket", t);
                }
            } else {
                EventType eventType;
                JsonElement elem = new JsonParser().parse(msg);
                try {
                    eventType = EventType.valueOf(elem.getAsJsonObject().get("update-type").getAsString());
                } catch (Throwable t) {
                    return;
                }
                try {
                    processIncomingEvent(msg, eventType);
                } catch (Throwable t) {
                    System.err.println("Failed to execute callback for event: " + eventType);
                    t.printStackTrace();
                    runOnError("Failed to execute callback for event: " + eventType, t);
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed to process message from websocket.");
            t.printStackTrace();
            runOnError("Failed to process message from websocket", t);
        }
    }

    private void processIncomingResponse(ResponseBase responseBase, Class type) {
        GetAuthRequiredResponse authRequiredResponse;
        AuthenticateResponse authenticateResponse;
        String str;
        switch ((str = type.getSimpleName()).hashCode()) {
            case -245944746:
                if (!"AuthenticateResponse".equals(str))
                    break;
                authenticateResponse = (AuthenticateResponse) responseBase;
                if ("ok".equals(authenticateResponse.getStatus())) {
                    runOnConnect(versionInfo);
                } else {
                    runOnConnectionFailed("Failed to authenticate with password. Error: " + authenticateResponse.getError());
                }
                return;
            case 468908830:
                if (!"GetAuthRequiredResponse".equals(str))
                    break;
                authRequiredResponse = (GetAuthRequiredResponse) responseBase;
                if (authRequiredResponse.isAuthRequired()) {
                    System.out.println("Authentication is required.");
                    authenticateWithServer(authRequiredResponse.getChallenge(), authRequiredResponse.getSalt());
                } else {
                    System.out.println("Authentication is not required. You're ready to go!");
                    runOnConnect(versionInfo);
                }
                return;
            case 663604003:
                if (!"GetVersionResponse".equals(str))
                    break;
                versionInfo = (GetVersionResponse) responseBase;
                System.out.printf("Connected to OBS. Websocket Version: %s, Studio Version: %s\n", versionInfo.getObsWebsocketVersion(), versionInfo.getObsStudioVersion());
                sendMessage(new Gson().toJson(new GetAuthRequiredRequest(this)));
                return;
        }
        if (!callbacks.containsKey(type)) {
            System.out.println("Invalid type received: " + type.getName());
            runOnError("Invalid response type received", new InvalidResponseTypeError(type.getName()));
            return;
        }
        try {
            callbacks.get(type).run(responseBase);
        } catch (Throwable t) {
            System.err.println("Failed to execute callback for response: " + type);
            t.printStackTrace();
            runOnError("Failed to execute callback for response: " + type, t);
        }
    }

    private void processIncomingEvent(String msg, EventType eventType) {
        switch (eventType) {
            case ReplayStarted -> Optional.ofNullable(onReplayStarted).ifPresent(v -> v.run(null));
            case ReplayStarting -> Optional.ofNullable(onReplayStarting).ifPresent(v -> v.run(null));
            case ReplayStopped -> Optional.ofNullable(onReplayStopped).ifPresent(v -> v.run(null));
            case ReplayStopping -> Optional.ofNullable(onReplayStopping).ifPresent(v -> v.run(null));
            case SwitchScenes ->
                    Optional.ofNullable(onSwitchScenes).ifPresent(v -> v.run(new Gson().fromJson(msg, SwitchScenesResponse.class)));
            case ScenesChanged ->
                    Optional.ofNullable(onScenesChanged).ifPresent(v -> v.run(new Gson().fromJson(msg, ScenesChangedResponse.class)));
            case TransitionBegin ->
                    Optional.ofNullable(onTransitionBegin).ifPresent(v -> v.run(new Gson().fromJson(msg, TransitionBeginResponse.class)));
            case TransitionEnd ->
                    Optional.ofNullable(onTransitionEnd).ifPresent(v -> v.run(new Gson().fromJson(msg, TransitionEndResponse.class)));
            case RecordingStopped -> Optional.ofNullable(onRecordingStopped).ifPresent(v -> v.run(null));
            case StreamStarted -> Optional.ofNullable(onStreamStarted).ifPresent(v -> v.run(null));
            case StreamStopped -> Optional.ofNullable(onStreamStopped).ifPresent(v -> v.run(null));
            case null, default -> Optional.ofNullable(onRecordingStarted).ifPresent(v -> v.run(null));
        }
    }

    private void authenticateWithServer(String challenge, String salt) {
        if (password == null) {
            System.err.println("Authentication required by server but no password set by client");
            runOnConnectionFailed("Authentication required by server but no password set by client");
            return;
        }
        String authResponse = generateAuthenticationResponseString(challenge, salt);
        if (authResponse == null)
            return;
        sendMessage(new Gson().toJson(new AuthenticateRequest(this, authResponse)));
    }

    private String generateAuthenticationResponseString(String challenge, String salt) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Failed to perform password authentication with server");
            e.printStackTrace();
            runOnConnectionFailed("Failed to perform password authentication with server");
            return null;
        }
        String secretString = password + salt;
        byte[] secretHash = digest.digest(secretString.getBytes(StandardCharsets.UTF_8));
        String encodedSecret = Base64.getEncoder().encodeToString(secretHash);
        String authResponseString = encodedSecret + challenge;
        byte[] authResponseHash = digest.digest(authResponseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(authResponseHash);
    }

    public void registerOnError(ErrorCallback onError) {
        this.onError = onError;
    }

    public void registerOnConnect(Callback onConnect) {
        this.onConnect = onConnect;
    }

    public void registerOnDisconnect(Callback onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public void registerOnConnectionFailed(StringCallback onConnectionFailed) {
        this.onConnectionFailed = onConnectionFailed;
    }

    public void registerOnReplayStarted(Callback onReplayStarted) {
        this.onReplayStarted = onReplayStarted;
    }

    public void registerOnReplayStarting(Callback onReplayStarting) {
        this.onReplayStarting = onReplayStarting;
    }

    public void registerOnReplayStopped(Callback onReplayStopped) {
        this.onReplayStopped = onReplayStopped;
    }

    public void registerOnReplayStopping(Callback onReplayStopping) {
        this.onReplayStopping = onReplayStopping;
    }

    public void registerOnSwitchScenes(Callback onSwitchScenes) {
        this.onSwitchScenes = onSwitchScenes;
    }

    public void registerOnScenesChanged(Callback onScenesChanged) {
        this.onScenesChanged = onScenesChanged;
    }

    public void registerOnTransitionBegin(Callback onTransitionBegin) {
        this.onTransitionBegin = onTransitionBegin;
    }

    public void registerOnTransitionEnd(Callback onTransitionEnd) {
        this.onTransitionEnd = onTransitionEnd;
    }

    public void registerOnRecordingStarted(Callback onRecordingStarted) {
        this.onRecordingStarted = onRecordingStarted;
    }

    public void registerOnRecordingStopped(Callback onRecordingStopped) {
        this.onRecordingStopped = onRecordingStopped;
    }

    public void registerOnStreamStarted(Callback onStreamStarted) {
        this.onStreamStarted = onStreamStarted;
    }

    public void registerOnStreamStopped(Callback onStreamStopped) {
        this.onStreamStopped = onStreamStopped;
    }

    public void getScenes(Callback callback) {
        sendMessage(new Gson().toJson(new GetSceneListRequest(this)));
        callbacks.put(GetSceneListResponse.class, callback);
    }

    public void getSourceTypes(Callback callback) {
        sendMessage(new Gson().toJson(new GetSourceTypeListRequest(this)));
        callbacks.put(GetSourceTypeListResponse.class, callback);
    }

    public void getSources(Callback callback) {
        sendMessage(new Gson().toJson(new GetSourceListRequest(this)));
        callbacks.put(GetSourceListResponse.class, callback);
    }

    public void setCurrentScene(String scene, Callback callback) {
        sendMessage(new Gson().toJson(new SetCurrentSceneRequest(this, scene)));
        callbacks.put(SetCurrentSceneResponse.class, callback);
    }

    public void setCurrentTransition(String transition, Callback callback) {
        sendMessage(new Gson().toJson(new SetCurrentTransitionRequest(this, transition)));
        callbacks.put(SetCurrentTransitionResponse.class, callback);
    }

    public void setSourceVisiblity(String scene, String source, boolean visibility, Callback callback) {
        SetSceneItemPropertiesRequest request = new SetSceneItemPropertiesRequest(this, scene, source, visibility);
        System.out.println(new Gson().toJson(request));
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetSceneItemPropertiesResponse.class, callback);
    }

    public void getSceneItemProperties(String scene, String source, Callback callback) {
        GetSceneItemPropertiesRequest request = new GetSceneItemPropertiesRequest(this, scene, source);
        System.out.println(new Gson().toJson(request));
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetSceneItemPropertiesResponse.class, callback);
    }

    public void getTransitionList(Callback callback) {
        GetTransitionListRequest request = new GetTransitionListRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetTransitionListResponse.class, callback);
    }

    public void transitionToProgram(String transitionName, int duration, Callback callback) {
        TransitionToProgramRequest request = new TransitionToProgramRequest(this, transitionName, duration);
        sendMessage(new Gson().toJson(request));
        callbacks.put(TransitionToProgramResponse.class, callback);
    }

    public void getSourceSettings(String sourceName, Callback callback) {
        GetSourceSettingsRequest request = new GetSourceSettingsRequest(this, sourceName);
        System.out.println(new Gson().toJson(request));
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetSourceSettingsResponse.class, callback);
    }

    public void setSourceSettings(String sourceName, Map<String, Object> settings, Callback callback) {
        SetSourceSettingsRequest request = new SetSourceSettingsRequest(this, sourceName, settings);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetSourceSettingsResponse.class, callback);
    }

    public void startRecording(Callback callback) {
        StartRecordingRequest request = new StartRecordingRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StartRecordingResponse.class, callback);
    }

    public void stopRecording(Callback callback) {
        StopRecordingRequest request = new StopRecordingRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StopRecordingResponse.class, callback);
    }

    public void getStreamingStatus(Callback callback) {
        GetStreamingStatusRequest request = new GetStreamingStatusRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetStreamingStatusResponse.class, callback);
    }

    public void startStreaming(Callback callback) {
        StartStreamingRequest request = new StartStreamingRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StartStreamingResponse.class, callback);
    }

    public void stopStreaming(Callback callback) {
        StopStreamingRequest request = new StopStreamingRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StopStreamingResponse.class, callback);
    }

    public void listProfiles(Callback callback) {
        ListProfilesRequest request = new ListProfilesRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(ListProfilesResponse.class, callback);
    }

    public void getCurrentProfile(Callback callback) {
        GetCurrentProfileRequest request = new GetCurrentProfileRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetCurrentProfileResponse.class, callback);
    }

    public void setCurrentProfile(String profile, Callback callback) {
        SetCurrentProfileRequest request = new SetCurrentProfileRequest(this, profile);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetCurrentProfileResponse.class, callback);
    }

    public void getCurrentScene(Callback callback) {
        GetCurrentSceneRequest request = new GetCurrentSceneRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetCurrentSceneResponse.class, callback);
    }

    public void getVolume(String source, Callback callback) {
        GetVolumeRequest request = new GetVolumeRequest(this, source);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetVolumeResponse.class, callback);
    }

    public void setVolume(String source, double volume, Callback callback) {
        SetVolumeRequest request = new SetVolumeRequest(this, source, volume);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetVolumeResponse.class, callback);
    }

    public void setMute(String source, boolean mute, Callback callback) {
        SetMuteRequest request = new SetMuteRequest(this, source, mute);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetMuteResponse.class, callback);
    }

    public void toggleMute(String source, Callback callback) {
        ToggleMuteRequest request = new ToggleMuteRequest(this, source);
        sendMessage(new Gson().toJson(request));
        callbacks.put(ToggleMuteResponse.class, callback);
    }

    public void getPreviewScene(Callback callback) {
        GetPreviewSceneRequest request = new GetPreviewSceneRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetPreviewSceneResponse.class, callback);
    }

    public void setPreviewScene(String name, Callback callback) {
        SetPreviewSceneRequest request = new SetPreviewSceneRequest(this, name);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetPreviewSceneResponse.class, callback);
    }

    public void getTransitionDuration(Callback callback) {
        GetTransitionDurationRequest request = new GetTransitionDurationRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetTransitionDurationResponse.class, callback);
    }

    public void setTransitionDuration(int duration, Callback callback) {
        SetTransitionDurationRequest request = new SetTransitionDurationRequest(this, duration);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetTransitionDurationResponse.class, callback);
    }

    public void startReplayBuffer(Callback callback) {
        StartReplayBufferRequest request = new StartReplayBufferRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StartReplayBufferResponse.class, callback);
    }

    public void stopReplayBuffer(Callback callback) {
        StopReplayBufferRequest request = new StopReplayBufferRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(StopReplayBufferResponse.class, callback);
    }

    public void saveReplayBuffer(Callback callback) {
        SaveReplayBufferRequest request = new SaveReplayBufferRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SaveReplayBufferResponse.class, callback);
    }

    public void getStudioModeEnabled(Callback callback) {
        GetStudioModeEnabledRequest request = new GetStudioModeEnabledRequest(this);
        sendMessage(new Gson().toJson(request));
        callbacks.put(GetStudioModeEnabledResponse.class, callback);
    }

    public void setStudioModeEnabled(boolean enabled, Callback callback) {
        SetStudioModeEnabledRequest request = new SetStudioModeEnabledRequest(this, enabled);
        sendMessage(new Gson().toJson(request));
        callbacks.put(SetStudioModeEnabledResponse.class, callback);
    }

    private void runOnError(String message, Throwable throwable) {
        if (onError == null)
            return;
        try {
            onError.run(message, throwable);
        } catch (Throwable t) {
            System.err.println("Exception during callback execution for 'onError'");
            t.printStackTrace();
        }
    }

    private void runOnConnectionFailed(String message) {
        if (onConnectionFailed == null)
            return;
        try {
            onConnectionFailed.run(message);
        } catch (Throwable t) {
            System.err.println("Exception during callback execution for 'onConnectionFailed'");
            t.printStackTrace();
        }
    }

    private void runOnConnect(GetVersionResponse versionInfo) {
        if (onConnect == null)
            return;
        try {
            onConnect.run(versionInfo);
        } catch (Throwable t) {
            System.err.println("Exception during callback execution for 'onConnect'");
            t.printStackTrace();
        }
    }

    private void sendMessage(String msg) {
        com.sendMessage(msg);
    }
}
