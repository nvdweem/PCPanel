package com.getpcpanel.obs;

import java.util.List;
import java.util.Map;

import com.getpcpanel.profile.SaveService;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * OBS integration stub - disabled due to javax/jakarta conflict.
 * TODO: Re-enable when obs4j migrates to jakarta.
 */
@Log4j2
@ApplicationScoped
public final class OBS {
    @Inject SaveService save;
    @Inject Event<Object> eventBus;

    @PostConstruct
    public void init() {
        log.info("OBS integration disabled (javax/jakarta conflict)");
    }

    public List<String> getSourcesWithAudio() {
        return List.of();
    }

    public Map<String, Boolean> getSourcesWithMuteState() {
        return Map.of();
    }

    public List<String> getScenes() {
        return List.of();
    }

    public void setSourceVolume(String sourceName, int vol) {}

    public void toggleSourceMute(String sourceName) {}

    public void setSourceMute(String sourceName, boolean mute) {}

    public void setCurrentScene(String sceneName) {}

    public boolean isConnected() {
        return false;
    }

    public String test(String address, int port, String password, long timeout) {
        return "OBS integration disabled";
    }
}
