package com.getpcpanel.integration.volume.platform.windows;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    /**
     * pid -&gt; session. Written by the audio backend's notification threads, read by the command thread
     * and by REST requests, so it is concurrent: those readers iterate it directly and a plain map can
     * be left mid-resize by a concurrent write.
     *
     * <p>Concurrency alone is not enough for {@link #addSession} / {@link #removeSession} though — each
     * is a read-then-write over both this map and the session's pointer set, and interleaving them
     * loses sessions (a departing session's removal can drop an entry another thread has just
     * re-registered). Both are synchronized on the device for that reason.
     */
    private final transient Map<Integer, WindowsAudioSession> sessions = new ConcurrentHashMap<>();
    /**
     * Everything this device and its sessions report comes from a {@code SndCtrl.dll} callback on a
     * thread the Windows audio API owns — and for session arrivals and departures, one that holds the
     * DLL's global audio lock. Those callbacks may not block, so every event goes out through this bus
     * rather than running observers on the caller — see {@link CallbackEventBus}.
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

    public synchronized AudioSession addSession(long pointer, int pid, String name, String title, String icon, float volume, boolean muted) {
        log.debug("Add device session: {} {} {} {} {} {} {}", pointer, pid, name, title, icon, volume, muted);
        var result = sessions.computeIfAbsent(pid, p -> new WindowsAudioSession(this, eventBus, pid, new File(name), title, icon, volume, muted));
        result.pointers().add(pointer);
        publish(new AudioSessionEvent(result, EventType.ADDED));
        return result;
    }

    public synchronized void removeSession(long pointer, int pid) {
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
            publish(new AudioSessionEvent(session, EventType.REMOVED));
        }
    }

    @Override
    protected void publish(Object event) {
        CallbackEventBus.fire(callbackEvents, event);
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
