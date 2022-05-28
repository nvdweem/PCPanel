package com.getpcpanel.cpp;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Setter(AccessLevel.PACKAGE)
@SuppressWarnings("unused") // Methods called from JNI
public class AudioDevice {
    public static final int eRender = 0;
    public static final int eCapture = 1;
    public static final int eAll = 2;

    private final String name;
    private final String id;
    private float volume;
    private boolean muted;
    private int dataflow;

    private Map<Integer, AudioSession> sessions = new HashMap<>();

    public Map<Integer, AudioSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    private void setState(float volume, boolean muted, int dataflow) {
        volume(volume).muted(muted).dataflow(dataflow);
        log.debug("State changed: {}", this);
    }

    private AudioSession addSession(int pid, String name, String title, String icon, float volume, boolean muted) {
        var result = new AudioSession(pid, new File(name), title, icon, volume, muted);
        if (StringUtils.isBlank(result.title())) {
            log.debug("Not adding {}, no title known", result);
        } else {
            sessions.put(pid, result);
            log.debug("Session added: {}", result);
        }
        return result;
    }

    private void removeSession(int pid) {
        sessions.remove(pid);
        log.debug("Session removed: {}", pid);
    }
}
