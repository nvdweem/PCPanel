package com.getpcpanel.cpp;

import java.io.File;
import java.io.Serializable;
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
public class AudioDevice implements Serializable {
    private final String name;
    private final String id;
    private float volume;
    private boolean muted;
    private DataFlow dataflow;

    private transient Map<Integer, AudioSession> sessions = new HashMap<>();

    public AudioDevice(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public Map<Integer, AudioSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    private void setState(float volume, boolean muted) {
        volume(volume).muted(muted);
        log.trace("State changed: {}", this);
    }

    private AudioSession addSession(int pid, String name, String title, String icon, float volume, boolean muted) {
        var result = new AudioSession(this, pid, new File(name), title, icon, volume, muted);
        if (StringUtils.isBlank(result.title())) {
            log.debug("Not adding {}, no title known", result);
        } else {
            sessions.put(pid, result);
            log.trace("Session added: {}", result);
        }
        return result;
    }

    private void removeSession(int pid) {
        var sess = sessions.remove(pid);
        log.trace("Session removed: {} ({})", pid, sess == null ? "not found" : sess);
    }

    public boolean isOutput() {
        return dataflow.output();
    }

    public String toString() {
        return name;
    }
}
