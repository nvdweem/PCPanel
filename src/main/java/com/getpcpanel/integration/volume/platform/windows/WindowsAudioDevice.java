package com.getpcpanel.integration.volume.platform.windows;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import jakarta.enterprise.event.Event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.AudioSessionEvent;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.EventType;

import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unused") // Methods called from JNI
class WindowsAudioDevice extends AudioDevice {
    private final transient Map<Integer, WindowsAudioSession> sessions = new HashMap<>(); // pid -> pointer_addr -> session
    /**
     * Session arrivals and departures are reported by the DLL while it holds its global audio lock, so
     * their observers must not run on that thread — see {@link CallbackEventBus}. Everything else this
     * device raises comes from a callback made without that lock and uses the plain bus.
     */
    @Nullable private final transient CallbackEventBus callbackEvents;

    public WindowsAudioDevice(Event<Object> eventBus, @Nullable CallbackEventBus callbackEvents, String name, String id) {
        super(eventBus, name, id);
        this.callbackEvents = callbackEvents;
    }

    @JsonIgnore
    public Map<Integer, WindowsAudioSession> getSessions() {
        return sessions;
    }

    public AudioSession addSession(long pointer, int pid, String name, String title, String icon, float volume, boolean muted) {
        log.debug("Add device session: {} {} {} {} {} {} {}", pointer, pid, name, title, icon, volume, muted);
        var result = sessions.computeIfAbsent(pid, p -> new WindowsAudioSession(this, eventBus, pid, new File(name), title, icon, volume, muted));
        result.pointers().add(pointer);
        CallbackEventBus.fire(callbackEvents, new AudioSessionEvent(result, EventType.ADDED));
        return result;
    }

    public void removeSession(long pointer, int pid) {
        var session = sessions.get(pid);
        if (session == null) {
            log.debug("Unknown session was removed: {} ({})", pid, pointer);
            return;
        }
        log.trace("Session pointer removed: {} ({}: {})", pid, pointer, session);
        session.pointers().remove(pointer);
        if (session.pointers().isEmpty()) {
            log.debug("Session removed: {} ({})", pid, pointer);
            sessions.remove(pid);
            CallbackEventBus.fire(callbackEvents, new AudioSessionEvent(session, EventType.REMOVED));
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
