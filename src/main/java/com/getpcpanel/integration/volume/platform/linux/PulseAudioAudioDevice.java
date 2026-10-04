package com.getpcpanel.integration.volume.platform.linux;

import jakarta.enterprise.event.Event;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.DataFlow;

import lombok.Getter;

@Getter
class PulseAudioAudioDevice extends AudioDevice {
    private final int index;
    private final boolean isDefault;
    private final boolean isOutput;

    public PulseAudioAudioDevice(Event<Object> eventBus, int index, String name, String id, boolean isDefault, boolean isOutput) {
        super(eventBus, name, id);
        this.index = index;
        this.isDefault = isDefault;
        this.isOutput = isOutput;
        dataflow(isOutput ? DataFlow.dfRender : DataFlow.dfCapture);
    }

    /** The volume and mute state as read from pactl, without announcing a change. */
    void state(float volume, boolean muted) {
        volume(volume);
        muted(muted);
    }

    public boolean isDefaultOutput() {
        return isDefault && isOutput;
    }

    public boolean isDefaultInput() {
        return isDefault && !isOutput;
    }

    @Override
    public String toString() {
        return super.toString() + " ("+(isOutput ? "out" : "in")+")";
    }
}
