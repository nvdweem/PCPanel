package com.getpcpanel.cpp;

import java.io.Serializable;

import jakarta.enterprise.event.Event;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@Setter(AccessLevel.PROTECTED)
@SuppressWarnings("unused") // Methods called from JNI
public class AudioDevice implements Serializable {
    protected final transient Event<Object> eventPublisher;
    private final String name;
    private final String id;
    private float volume;
    private boolean muted;
    private DataFlow dataflow;

    public AudioDevice(Event<Object> eventPublisher, String name, String id) {
        this.eventPublisher = eventPublisher;
        this.name = name;
        this.id = id;
    }

    private void setState(float volume, boolean muted) {
        volume(volume).muted(muted);
        eventPublisher.fire(new AudioDeviceEvent(this, EventType.CHANGED));
        log.trace("State changed: {}", this);
    }

    public boolean isOutput() {
        return dataflow.output();
    }

    public boolean isInput() {
        return dataflow.input();
    }

    public String toString() {
        return name;
    }
}
