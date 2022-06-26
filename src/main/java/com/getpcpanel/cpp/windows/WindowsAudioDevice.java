package com.getpcpanel.cpp.windows;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.EventType;

import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unused") // Methods called from JNI
public class WindowsAudioDevice extends AudioDevice {
    private final transient Map<Integer, WindowsAudioSession> sessions = new HashMap<>(); // pid -> pointer_addr -> session

    public WindowsAudioDevice(ApplicationEventPublisher eventPublisher, String name, String id) {
        super(eventPublisher, name, id);
    }

    public Map<Integer, WindowsAudioSession> getSessions() {
        return sessions;
    }

    public AudioSession addSession(long pointer, int pid, String name, String title, String icon, float volume, boolean muted) {
        var result = sessions.computeIfAbsent(pid, p -> new WindowsAudioSession(this, eventPublisher, pid, new File(name), title, icon, volume, muted));
        result.pointers().add(pointer);

        if (StringUtils.isBlank(result.title())) {
            log.debug("Not adding {}, no title known", result);
        } else {
            sessions.put(pid, result);
            log.trace("Session added: {}", result);
        }
        eventPublisher.publishEvent(new AudioSessionEvent(result, EventType.ADDED));
        return result;
    }

    public void removeSession(long pointer, int pid) {
        var session = sessions.get(pid);
        if (session == null) {
            log.debug("Unknown session was removed: {} ({})", pid, pointer);
            return;
        }
        log.trace("Session removed: {} ({}: {})", pid, pointer, session);
        session.pointers().remove(pointer);
        if (session.pointers().isEmpty()) {
            sessions.remove(pid);
            eventPublisher.publishEvent(new AudioSessionEvent(session, EventType.REMOVED));
        }
    }

    @Override
    protected WindowsAudioDevice volume(float volume) {
        super.volume(volume);
        return this;
    }

    @Override
    protected WindowsAudioDevice muted(boolean muted) {
        super.muted(muted);
        return this;
    }

    @Override
    protected WindowsAudioDevice dataflow(DataFlow dataflow) {
        super.dataflow(dataflow);
        return this;
    }
}
