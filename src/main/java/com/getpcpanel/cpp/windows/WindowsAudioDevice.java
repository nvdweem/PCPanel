package com.getpcpanel.cpp.windows;

import java.io.File;
import java.util.Collections;
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
    private final transient Map<Integer, WindowsAudioSession> sessions = new HashMap<>();

    public WindowsAudioDevice(ApplicationEventPublisher eventPublisher, String name, String id) {
        super(eventPublisher, name, id);
    }

    public Map<Integer, WindowsAudioSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    private AudioSession addSession(int pid, String name, String title, String icon, float volume, boolean muted) {
        var result = new WindowsAudioSession(this, eventPublisher, pid, new File(name), title, icon, volume, muted);
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
