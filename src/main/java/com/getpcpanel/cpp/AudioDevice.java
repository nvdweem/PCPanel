package com.getpcpanel.cpp;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonIgnore protected final transient Event<Object> eventBus;
    @JsonProperty private final String name;
    @JsonProperty private final String id;
    private float volume;
    private boolean muted;
    @JsonProperty private DataFlow dataflow;

    public AudioDevice(Event<Object> eventBus, String name, String id) {
        this.eventBus = eventBus;
        this.name = name;
        this.id = id;
    }

    private void setState(float volume, boolean muted) {
        volume(volume).muted(muted);
        eventBus.fire(new AudioDeviceEvent(this, EventType.CHANGED));
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
