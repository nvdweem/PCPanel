package obsremote;

import obsremote.callbacks.Callback;
import obsremote.callbacks.ErrorCallback;
import obsremote.callbacks.StringCallback;
import obsremote.objects.throwables.OBSResponseError;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import util.Util;

import java.net.URI;
import java.util.Map;

public class OBSRemoteController {
    private final String address;

    private final OBSCommunicator communicator;

    private WebSocketClient client;

    private StringCallback onConnectionFailed;

    private ErrorCallback onError;

    private boolean failed;

    public OBSRemoteController(String address, boolean debug, String password, boolean autoConnect) {
        if (Util.isNullOrEmpty(password))
            password = null;
        this.address = address;
        communicator = new OBSCommunicator(debug, password);
        if (autoConnect)
            connect();
    }

    public OBSRemoteController(String address, boolean debug, String password) {
        this(address, debug, password, true);
    }

    public OBSRemoteController(String address, boolean debug) {
        this(address, debug, null);
    }

    public OBSRemoteController(String address, String port, String password) {
        if (Util.isNullOrEmpty(address))
            address = "localhost";
        if (Util.isNullOrEmpty(port))
            port = "4444";
        if (Util.isNullOrEmpty(password))
            password = null;
        this.address = "ws://" + address + ":" + port;
        communicator = new OBSCommunicator(false, password);
        connect();
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
            System.err.println("Failed to start WebSocketClient.");
            e.printStackTrace();
            runOnError("Failed to start WebSocketClient", e);
        }
    }

    public boolean isConnected() {
        return client.isOpen();
    }

    public void disconnect() {
        try {
            client.closeBlocking();
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    public void getScenes(Callback callback) {
        communicator.getScenes(callback);
    }

    public void getSourceTypes(Callback callback) {
        communicator.getSourceTypes(callback);
    }

    public void getSources(Callback callback) {
        communicator.getSources(callback);
    }

    public void registerOnError(ErrorCallback onError) {
        this.onError = onError;
        communicator.registerOnError(onError);
    }

    public void registerConnectCallback(Callback onConnect) {
        communicator.registerOnConnect(onConnect);
    }

    public void registerDisconnectCallback(Callback onDisconnect) {
        communicator.registerOnDisconnect(onDisconnect);
    }

    public void registerConnectionFailedCallback(StringCallback onConnectionFailed) {
        this.onConnectionFailed = onConnectionFailed;
        communicator.registerOnConnectionFailed(onConnectionFailed);
    }

    public void registerRecordingStartedCallback(Callback onRecordingStarted) {
        communicator.registerOnRecordingStarted(onRecordingStarted);
    }

    public void registerRecordingStoppedCallback(Callback onRecordingStopped) {
        communicator.registerOnRecordingStopped(onRecordingStopped);
    }

    public void registerReplayStartedCallback(Callback onReplayStarted) {
        communicator.registerOnReplayStarted(onReplayStarted);
    }

    public void registerReplayStartingCallback(Callback onReplayStarting) {
        communicator.registerOnReplayStarting(onReplayStarting);
    }

    public void registerReplayStoppedCallback(Callback onReplayStopped) {
        communicator.registerOnReplayStopped(onReplayStopped);
    }

    public void registerReplayStoppingCallback(Callback onReplayStopping) {
        communicator.registerOnReplayStopping(onReplayStopping);
    }

    public void registerStreamStartedCallback(Callback onRecordingStarted) {
        communicator.registerOnStreamStarted(onRecordingStarted);
    }

    public void registerStreamStoppedCallback(Callback onRecordingStopped) {
        communicator.registerOnStreamStopped(onRecordingStopped);
    }

    public void registerSwitchScenesCallback(Callback onSwitchScenes) {
        communicator.registerOnSwitchScenes(onSwitchScenes);
    }

    public void registerScenesChangedCallback(Callback onScenesChanged) {
        communicator.registerOnScenesChanged(onScenesChanged);
    }

    public void registerTransitionBeginCallback(Callback onTransitionBegin) {
        communicator.registerOnTransitionBegin(onTransitionBegin);
    }

    public void registerTransitionEndCallback(Callback onTransitionEnd) {
        communicator.registerOnTransitionEnd(onTransitionEnd);
    }

    public void await() throws InterruptedException {
        communicator.await();
    }

    public void setCurrentScene(String szene, Callback callback) {
        communicator.setCurrentScene(szene, callback);
    }

    public void setCurrentTransition(String transition, Callback callback) {
        communicator.setCurrentTransition(transition, callback);
    }

    public void changeSceneWithTransition(String scene, String transition, Callback callback) {
        communicator.setCurrentTransition(transition, response -> {
            if (!"ok".equals(response.getStatus())) {
                System.out.println("Failed to change transition. Pls fix.");
                runOnError("Error response for changeSceneWithTransition", new OBSResponseError(response.getError()));
            }
            communicator.setCurrentScene(scene, callback);
        });
    }

    public void setSourceVisibility(String scene, String source, boolean visibility, Callback callback) {
        communicator.setSourceVisiblity(scene, source, visibility, callback);
    }

    public void getSceneItemProperties(String scene, String source, Callback callback) {
        communicator.getSceneItemProperties(scene, source, callback);
    }

    public void getTransitionList(Callback callback) {
        communicator.getTransitionList(callback);
    }

    public void transitionToProgram(String transitionName, int duration, Callback callback) {
        communicator.transitionToProgram(transitionName, duration, callback);
    }

    public void getSourceSettings(String sourceName, Callback callback) {
        communicator.getSourceSettings(sourceName, callback);
    }

    public void setSourceSettings(String sourceName, Map<String, Object> settings, Callback callback) {
        communicator.setSourceSettings(sourceName, settings, callback);
    }

    public void getStreamingStatus(Callback callback) {
        communicator.getStreamingStatus(callback);
    }

    public void startStreaming(Callback callback) {
        communicator.startStreaming(callback);
    }

    public void stopStreaming(Callback callback) {
        communicator.stopStreaming(callback);
    }

    public void startRecording(Callback callback) {
        communicator.startRecording(callback);
    }

    public void stopRecording(Callback callback) {
        communicator.stopRecording(callback);
    }

    public void listProfiles(Callback callback) {
        communicator.listProfiles(callback);
    }

    public void getCurrentProfile(Callback callback) {
        communicator.getCurrentProfile(callback);
    }

    public void setCurrentProfile(String profile, Callback callback) {
        communicator.setCurrentProfile(profile, callback);
    }

    public void getCurrentScene(Callback callback) {
        communicator.getCurrentScene(callback);
    }

    public void getVolume(String source, Callback callback) {
        communicator.getVolume(source, callback);
    }

    public void setVolume(String source, double volume, Callback callback) {
        communicator.setVolume(source, volume, callback);
    }

    public void setMute(String source, boolean mute, Callback callback) {
        communicator.setMute(source, mute, callback);
    }

    public void toggleMute(String source, Callback callback) {
        communicator.toggleMute(source, callback);
    }

    public void getPreviewScene(Callback callback) {
        communicator.getPreviewScene(callback);
    }

    public void setPreviewScene(String name, Callback callback) {
        communicator.setPreviewScene(name, callback);
    }

    public void getTransitionDuration(Callback callback) {
        communicator.getTransitionDuration(callback);
    }

    public void setTransitionDuration(int duration, Callback callback) {
        communicator.setTransitionDuration(duration, callback);
    }

    public void getStudioModeEnabled(Callback callback) {
        communicator.getStudioModeEnabled(callback);
    }

    public void setStudioModeEnabled(boolean enabled, Callback callback) {
        communicator.setStudioModeEnabled(enabled, callback);
    }

    public void startReplayBuffer(Callback callback) {
        communicator.startReplayBuffer(callback);
    }

    public void stopReplayBuffer(Callback callback) {
        communicator.stopReplayBuffer(callback);
    }

    public void saveReplayBuffer(Callback callback) {
        communicator.saveReplayBuffer(callback);
    }

    private void runOnError(String message, Throwable throwable) {
        if (onError == null)
            return;
        try {
            onError.run(message, throwable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runOnConnectionFailed(String message) {
        if (onConnectionFailed == null)
            return;
        try {
            onConnectionFailed.run(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
