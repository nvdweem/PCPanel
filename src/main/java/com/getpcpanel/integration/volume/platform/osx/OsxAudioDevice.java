package com.getpcpanel.integration.volume.platform.osx;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioDeviceEvent;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.CoreAudioDevice;

import jakarta.enterprise.event.Event;
import lombok.Getter;

@Getter
class OsxAudioDevice extends AudioDevice {
    private final int deviceId;
    private final boolean isDefault;

    public OsxAudioDevice(Event<Object> eventBus, CoreAudioDevice device) {
        super(eventBus, device.name(), device.uid());
        deviceId = device.id();
        isDefault = device.isDefault();
        dataflow(device.output() ? DataFlow.dfRender : DataFlow.dfCapture);
        volume(device.volume());
        muted(device.muted());
    }

    public boolean defaultOutput() {
        return isDefault && isOutput();
    }

    public boolean defaultInput() {
        return isDefault && isInput();
    }

    /**
     * Updates volume/mute state and publishes a change event when it actually changed.
     */
    boolean updateState(float volume, boolean muted) {
        if (Math.round(volume() * 100) == Math.round(volume * 100) && muted() == muted) {
            return false;
        }
        volume(volume);
        muted(muted);
        eventBus.fire(new AudioDeviceEvent(this, EventType.CHANGED));
        return true;
    }
}
