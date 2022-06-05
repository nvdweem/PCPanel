package com.getpcpanel.obs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.getpcpanel.obs.remote.OBSRemoteController;
import com.getpcpanel.obs.remote.objects.Source;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public final class OBS {
    public volatile OBSRemoteController controller;
    public static final Object OBSMutex = new Object();
    private static final long WAIT_TIME = 1000L;
    private final SaveService save;

    public List<String> getSourcesWithAudio() {
        var sourcesWithAudio = new ArrayList<String>();
        var typesWithAudio = new HashSet<String>();
        try {
            controller.getSourceTypes(response -> {
                for (var st : response.getTypes()) {
                    if (st.getCaps().isHasAudio())
                        typesWithAudio.add(st.getTypeId());
                }
                controller.getSources(sr -> StreamEx.of(sr.getSources()).filter(s -> typesWithAudio.contains(s.getTypeId())).map(Source::getName).into(sourcesWithAudio));
            });
            synchronized (sourcesWithAudio) {
                sourcesWithAudio.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to get sources with audio", e);
        }
        return sourcesWithAudio;
    }

    public List<String> getScenes() {
        List<String> scenes = new ArrayList<>();
        try {
            controller.getScenes(response -> {
                synchronized (scenes) {
                    if (response.getScenes() != null)
                        for (var scene : response.getScenes())
                            scenes.add(scene.getName());
                    scenes.notify();
                }
            });
            synchronized (scenes) {
                scenes.wait(WAIT_TIME);
            }
        } catch (Exception e) {
            log.error("Unable to get scenes", e);
        }
        return scenes;
    }

    public void setSourceVolume(String sourceName, int vol) {
        if (controller == null)
            return;
        var waiter = new Object();
        try {
            var decimal = vol / 100.0D;
            controller.setVolume(sourceName, decimal, c -> {
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
        var waiter = new Object();
        try {
            controller.toggleMute(sourceName, c -> {
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
        var waiter = new Object();
        try {
            controller.setMute(sourceName, mute, c -> {
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
        var waiter = new Object();
        try {
            controller.setCurrentScene(sceneName, c -> {
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
        return save.get().isObsEnabled() && controller != null && controller.isConnected();
    }
}
