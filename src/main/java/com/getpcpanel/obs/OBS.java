package com.getpcpanel.obs;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;

import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.listener.lifecycle.ReasonThrowable;
import io.obswebsocket.community.client.model.Input;
import io.obswebsocket.community.client.model.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public final class OBS {
    private static final long WAIT_TIME = 1000L;
    private final SaveService save;
    private List<Object> previousSettings = List.of();
    private boolean connected;
    private boolean shuttingDown;
    @Nullable private OBSRemoteController controller;

    @PostConstruct
    public void init() {
        var thread = new Thread(this::buildAndConnectObsController, "OBS Connection Starter");
        thread.setDaemon(true);
        thread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::applicationEnding, "OBS Shutdown hook"));
    }

    private void applicationEnding() {
        shuttingDown = true;
        if (controller != null) {
            controller.disconnect();
            controller = null;
        }
    }

    private void buildAndConnectObsController() {
        var port = NumberUtils.toInt(save.get().getObsPort(), -1);
        var address = save.get().getObsAddress();
        var password = StringUtils.trimToNull(save.get().getObsPassword());
        if (settingsStillSame() && controller != null)
            return;

        if (controller != null) {
            controller.disconnect();
            controller = null;
        }

        if (port != -1 && StringUtils.isNotBlank(address)) {
            controller = OBSRemoteController.builder()
                                            .autoConnect(false)
                                            .host(address)
                                            .port(port)
                                            .password(password)
                                            .lifecycle()
                                            .withControllerDefaultLogging(false)
                                            .withCommunicatorDefaultLogging(false)
                                            .onReady(this::connected)
                                            .onDisconnect(this::tryReconnect)
                                            .onControllerError(this::onError)
                                            .and()
                                            .build();
            controller.connect();
        } else {
            controller = null;
            connected = false;
        }
    }

    private void onError(ReasonThrowable reasonThrowable) {
        if (reasonThrowable.getThrowable() instanceof ConnectException || reasonThrowable.getThrowable() instanceof TimeoutException) {
            tryReconnect();
        } else {
            log.error("Unknown OBS error", reasonThrowable.getThrowable());
        }
    }

    private void tryReconnect() {
        if (shuttingDown) {
            return;
        }
        connected = false;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.warn("Unable to sleep");
        }
        if (controller != null) {
            log.debug("Reconnecting to OBS");
            controller.connect();
        } else {
            buildAndConnectObsController();
        }
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
        connected = true;
    }

    @EventListener(SaveService.SaveEvent.class)
    public void saveUpdated() {
        if (!settingsStillSame()) {
            buildAndConnectObsController();
        }
    }

    public List<String> getSourcesWithAudio() {
        var sourcesWithAudio = new ArrayList<String>();
        controller.getInputListRequest("", e -> {
            synchronized (sourcesWithAudio) {
                StreamEx.of(e.getMessageData().getResponseData().getInputs()).map(Input::getInputName).into(sourcesWithAudio);
                sourcesWithAudio.notify();
            }
        });
        synchronized (sourcesWithAudio) {
            try {
                sourcesWithAudio.wait(WAIT_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return sourcesWithAudio;
    }

    public List<String> getScenes() {
        if (!isConnected()) {
            return List.of();
        }

        var scenes = new ArrayList<String>();
        controller.getSceneList(ss -> {
            synchronized (scenes) {
                StreamEx.of(ss.getMessageData().getResponseData().getScenes()).map(Scene::getSceneName).into(scenes);
                scenes.notify();
            }
        });
        synchronized (scenes) {
            try {
                scenes.wait(WAIT_TIME);
            } catch (InterruptedException e) {
                log.error("Unable to get scenes", e);
            }
        }
        return scenes;
    }

    public void setSourceVolume(String sourceName, int vol) {
        if (!isConnected()) {
            return;
        }
        var waiter = new Object();
        try {
            var decimal = (float) Util.map(vol, 0, 100, -97, 0);
            controller.setInputVolumeRequest(sourceName, decimal, null, x -> {
                synchronized (waiter) {
                    waiter.notify();
                }
            });
            synchronized (waiter) {
                waiter.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to get source volume", e);
        }
    }

    public void toggleSourceMute(String sourceName) {
        if (!isConnected()) {
            return;
        }
        var waiter = new Object();
        try {
            controller.toggleInputMuteRequest(sourceName, c -> {
                synchronized (waiter) {
                    waiter.notify();
                }
            });
            synchronized (waiter) {
                waiter.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to toggle source mute {}", sourceName, e);
        }
    }

    public void setSourceMute(String sourceName, boolean mute) {
        if (!isConnected()) {
            return;
        }
        var waiter = new Object();
        try {
            controller.setInputMuteRequest(sourceName, mute, c -> {
                synchronized (waiter) {
                    waiter.notify();
                }
            });
            synchronized (waiter) {
                waiter.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to set source mute {} {}", sourceName, mute, e);
        }
    }

    public void setCurrentScene(String sceneName) {
        if (!isConnected()) {
            return;
        }
        var waiter = new Object();
        try {
            controller.setCurrentProgramSceneRequest(sceneName, c -> {
                synchronized (waiter) {
                    waiter.notify();
                }
            });
            synchronized (waiter) {
                waiter.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to set current scene to {}", sceneName, e);
        }
    }

    public boolean isConnected() {
        return save.get().isObsEnabled() && controller != null && connected;
    }
}
