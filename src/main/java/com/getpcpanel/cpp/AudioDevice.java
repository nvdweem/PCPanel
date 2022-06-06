package com.getpcpanel.cpp;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@Setter(AccessLevel.PACKAGE)
@SuppressWarnings("unused") // Methods called from JNI
public class AudioDevice implements Serializable {
    private final ApplicationEventPublisher eventPublisher;
    private final String name;
    private final String id;
    private float volume;
    private boolean muted;
    private DataFlow dataflow;

    private transient Map<Integer, AudioSession> sessions = new HashMap<>();

    public AudioDevice(ApplicationEventPublisher eventPublisher, String name, String id) {
        this.eventPublisher = eventPublisher;
        this.name = name;
        this.id = id;
    }

    public Map<Integer, AudioSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    private void setState(float volume, boolean muted) {
        volume(volume).muted(muted);
        eventPublisher.publishEvent(new AudioDeviceEvent(this, EventType.CHANGED));
        log.trace("State changed: {}", this);
    }

    private AudioSession addSession(int pid, String name, String title, String icon, float volume, boolean muted) {
        var result = new AudioSession(this, eventPublisher, pid, new File(name), title, icon, volume, muted);
        if (StringUtils.isBlank(result.title())) {
            log.debug("Not adding {}, no title known", result);
        } else {
            sessions.put(pid, result);
            log.trace("Session added: {}", result);
        }
        eventPublisher.publishEvent(new AudioSessionEvent(result, EventType.ADDED));
        return result;
    }

    private void removeSession(int pid) {
        var sess = sessions.remove(pid);
        log.trace("Session removed: {} ({})", pid, sess == null ? "not found" : sess);
        eventPublisher.publishEvent(new AudioSessionEvent(sess, EventType.REMOVED));
    }

    public boolean isOutput() {
        return dataflow.output();
    }

    public String toString() {
        return name;
    }
}
