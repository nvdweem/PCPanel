package com.getpcpanel.cpp.linux.pulseaudio;

import jakarta.enterprise.event.Event;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;

import lombok.Getter;

@Getter
public class PulseAudioAudioDevice extends AudioDevice {
    private final int index;
    private final boolean isDefault;
    private final boolean isOutput;

    public PulseAudioAudioDevice(Event<Object> eventBus, int index, String name, String id, boolean isDefault, boolean isOutput) {
        super(eventBus, name, id);
        this.index = index;
        this.isDefault = isDefault;
        this.isOutput = isOutput;
        dataflow(DataFlow.dfRender);
    }

    public boolean isDefaultOutput() {
        return isDefault && isOutput;
    }

    @Override
    public String toString() {
        return super.toString() + " ("+(isOutput ? "out" : "in")+")";
    }
}
