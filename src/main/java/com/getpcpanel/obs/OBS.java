package com.getpcpanel.obs;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;

import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.OBSRemoteControllerBuilder;
import io.obswebsocket.community.client.listener.lifecycle.ReasonThrowable;
import io.obswebsocket.community.client.message.event.inputs.InputMuteStateChangedEvent;
import io.obswebsocket.community.client.message.request.RequestBatch;
import io.obswebsocket.community.client.message.request.inputs.GetInputMuteRequest;
import io.obswebsocket.community.client.message.response.RequestBatchResponse;
import io.obswebsocket.community.client.message.response.RequestResponse;
import io.obswebsocket.community.client.message.response.inputs.GetInputMuteResponse;
import io.obswebsocket.community.client.model.Input;
import io.obswebsocket.community.client.model.Scene;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public final class OBS {
    private static final long WAIT_TIME_MS = 1000L;
    private static final ObsIdHelper OBS_ID_HELPER = new ObsIdHelper();

    private final SaveService save;
    private final ApplicationEventPublisher eventPublisher;
    private List<Object> previousSettings = List.of();
    private boolean connected;
    private boolean shuttingDown;
    @Nullable private OBSRemoteController controller;

    @PostConstruct
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::applicationEnding, "OBS Shutdown hook"));
    }

    @Scheduled(fixedRateString = "${pcpanel.obs.rate:2500}")
    public void connect() {
        if (!connected && !shuttingDown) {
            buildAndConnectObsController();
        }
    }

    private void applicationEnding() {
        shuttingDown = true;
        disconnectController();
    }

    private void buildAndConnectObsController() {
        var save = this.save.get();
        if (!save.isObsEnabled() || connected || shuttingDown) {
            log.trace("Obs is disabled({})/already connected({})/we are shutting down({})", save.isObsEnabled(), connected, shuttingDown);
            disconnectController();
            return;
        }

        try {
            doBuildAndConnectObsController();
        } catch (Exception e) {
            doConnected(false);
            connected = false;
            log.debug("Connecting failed", e);
        }
    }

    private void doBuildAndConnectObsController() {
        var save = this.save.get();
        log.debug("Connecting to OBS");
        if (settingsStillSame() && controller != null) {
            connected = true;
            controller.connect();
            return;
        }

        disconnectController();
        connected = true;
        var port = NumberUtils.toInt(save.getObsPort(), -1);
        var address = save.getObsAddress();
        var password = StringUtils.trimToNull(save.getObsPassword());

        if (port != -1 && StringUtils.isNotBlank(address)) {
            var currentIdx = OBS_ID_HELPER.incAndGet();
            controller = buildController(address, port, password).lifecycle()
                                                                 .onReady(this::connected)
                                                                 .onDisconnect(() -> OBS_ID_HELPER.runIfIdEq(currentIdx, () -> {
                                                                     doConnected(false);
                                                                     connected = false;
                                                                 }))
                                                                 .onControllerError(e -> OBS_ID_HELPER.runIfIdEq(currentIdx, () -> onError(e)))
                                                                 .and()
                                                                 .registerEventListener(InputMuteStateChangedEvent.class, this::onMuteChanged)
                                                                 .build();
            controller.connect();
        } else {
            doConnected(false);
            connected = false;
        }
    }

    private void onMuteChanged(InputMuteStateChangedEvent t) {
        eventPublisher.publishEvent(new OBSMuteEvent(t.getMessageData().getEventData().getInputName(), t.getMessageData().getEventData().getInputMuted()));
    }

    private void disconnectController() {
        doConnected(false);
        connected = false;
        if (controller != null) {
            controller.disconnect();
            controller.stop();
            controller = null;
        }
    }

    @Nullable
    public String test(String address, int port, String password, long timeout) {
        var latch = new CountDownLatch(1);
        var result = new String[1];
        Consumer<String> doResult = str -> {
            result[0] = str;
            latch.countDown();
        };

        var controller = buildController(address, port, password).lifecycle()
                                                                 .onReady(() -> doResult.accept(null))
                                                                 .onDisconnect(latch::countDown)
                                                                 .onControllerError(e -> doResult.accept(e.getReason()))
                                                                 .onCommunicatorError(e -> doResult.accept(e.getReason()))
                                                                 .onClose(e -> doResult.accept(e.name()))
                                                                 .and().build();
        controller.connect();

        try {
            var waitSuccess = latch.await(timeout, TimeUnit.MILLISECONDS);
            var message = waitSuccess && result[0] == null ? null : result[0];
            controller.disconnect();
            controller.stop();
            return message;
        } catch (InterruptedException e) {
            log.warn("Unable to wait for the latch");
        }
        return null;
    }

    private OBSRemoteControllerBuilder buildController(String address, int port, String password) {
        return OBSRemoteController.builder()
                                  .autoConnect(false)
                                  .host(address)
                                  .port(port)
                                  .password(password)
                                  .lifecycle()
                                  .withControllerDefaultLogging(false)
                                  .withCommunicatorDefaultLogging(false)
                                  .and();
    }

    private void onError(ReasonThrowable reasonThrowable) {
        var exception = reasonThrowable.getThrowable();
        if (exception instanceof ExecutionException exEx) {
            exception = exEx.getCause();
        }

        if (exception instanceof SocketTimeoutException || exception instanceof TimeoutException || exception instanceof ConnectException) {
            log.debug("Timeout/connect exception occurred", exception);
        } else {
            log.warn("Unknown OBS error, stack is logged in debug");
            log.debug("Unknown OBS error", exception);
        }
        doConnected(false);
        connected = false;
    }

    private boolean settingsStillSame() {
        var port = NumberUtils.toInt(save.get().getObsPort(), -1);
        var address = save.get().getObsAddress();
        var password = StringUtils.trimToNull(save.get().getObsPassword());
        var settings = List.<Object>of(port, Objects.requireNonNullElse(address, "-"), Objects.requireNonNullElse(password, "-"));
        if (settings.equals(previousSettings)) {
            return true;
        }
        previousSettings = settings;
        return false;
    }

    private void connected() {
        log.info("Connected to OBS");
        doConnected(true);
        connected = true;
    }

    @EventListener(SaveService.SaveEvent.class)
    public void saveUpdated() {
        buildAndConnectObsController();
    }

    public List<String> getSourcesWithAudio() {
        var nameToMute = getSourcesWithMuteState();
        return new ArrayList<>(nameToMute.keySet());
    }

    public Map<String, Boolean> getSourcesWithMuteState() {
        if (!isConnected() || controller == null) {
            return Map.of();
        }
        var sources = controller.getInputList(null, WAIT_TIME_MS).getInputs();
        return getNameToMuteState(sources);
    }

    public List<String> getScenes() {
        if (!isConnected() || controller == null) {
            return List.of();
        }

        return Optional.ofNullable(controller.getSceneList(WAIT_TIME_MS))
                       .map(ss -> StreamEx.of(ss.getScenes()).map(Scene::getSceneName).toList())
                       .orElse(List.of());
    }

    public void setSourceVolume(String sourceName, int vol) {
        if (!isConnected() || controller == null) {
            return;
        }
        try {
            var decimal = (float) Util.map(vol, 0, 100, -97, 0);
            controller.setInputVolume(sourceName, null, decimal, WAIT_TIME_MS);
        } catch (Exception e) {
            log.error("Unable to get source volume", e);
        }
    }

    public void toggleSourceMute(String sourceName) {
        if (!isConnected() || controller == null) {
            return;
        }
        try {
            controller.toggleInputMute(sourceName, WAIT_TIME_MS);
        } catch (Exception e) {
            log.error("Unable to toggle source mute {}", sourceName, e);
        }
    }

    public void setSourceMute(String sourceName, boolean mute) {
        if (!isConnected() || controller == null) {
            return;
        }
        try {
            controller.setInputMute(sourceName, mute, WAIT_TIME_MS);
        } catch (Exception e) {
            log.error("Unable to set source mute {} {}", sourceName, mute, e);
        }
    }

    public void setCurrentScene(String sceneName) {
        if (!isConnected() || controller == null) {
            return;
        }
        try {
            controller.setCurrentProgramScene(sceneName, WAIT_TIME_MS);
        } catch (Exception e) {
            log.error("Unable to set current scene to {}", sceneName, e);
        }
    }

    public boolean isConnected() {
        return save.get().isObsEnabled() && controller != null && connected;
    }

    private void doConnected(boolean connected) {
        new Thread(() -> {
            eventPublisher.publishEvent(new OBSConnectEvent(connected));
            if (connected) {
                getSourcesWithMuteState();
            }
        }).start();
    }

    private Map<String, Boolean> getNameToMuteState(List<Input> sources) {
        if (controller == null) {
            return Map.of();
        }
        record RequestAndName(GetInputMuteRequest request, String name) {
        }

        var muteRequests = StreamEx.of(sources)
                                   .map(source -> {
                                       var req = GetInputMuteRequest.builder().inputName(source.getInputName()).build();
                                       return new RequestAndName(req, source.getInputName());
                                   })
                                   .mapToEntry(rn -> rn.request.getRequestId(), Function.identity())
                                   .toMap();

        var latch = new ArrayBlockingQueue<RequestBatchResponse>(1);
        controller.sendRequestBatch(RequestBatch.builder().requests(StreamEx.ofValues(muteRequests).map(RequestAndName::request).toList()).build(), latch::offer);

        try {
            var result = latch.poll(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                return Map.of();
            }
            return StreamEx.of(result.getData().getResults())
                           .mapToEntry(rs -> muteRequests.get(rs.getRequestId()), RequestResponse.Data::getResponseData)
                           .nonNullKeys().mapKeys(rn -> rn.name)
                           .nonNullValues()
                           .selectValues(GetInputMuteResponse.SpecificData.class)
                           .mapValues(GetInputMuteResponse.SpecificData::getInputMuted)
                           .toMap();
        } catch (InterruptedException e) {
            return Map.of();
        }
    }

    static class ObsIdHelper {
        private int activeIdx;

        private int incAndGet() {
            activeIdx++;
            return activeIdx;
        }

        private void runIfIdEq(int id, Runnable toRun) {
            if (activeIdx == id) {
                toRun.run();
            }
        }
    }
}
