package com.getpcpanel.cpp.osx;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioDeviceEvent;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.osx.CoreAudioWrapper.CoreAudioDevice;

import lombok.Getter;

@Getter
public class OsxAudioDevice extends AudioDevice {
    private final int deviceId;
    private final boolean isDefault;

    public OsxAudioDevice(ApplicationEventPublisher eventPublisher, CoreAudioDevice device) {
        super(eventPublisher, device.name(), device.uid());
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
        eventPublisher.publishEvent(new AudioDeviceEvent(this, EventType.CHANGED));
        return true;
    }
}
