package com.getpcpanel.cpp.windows;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;

public class WindowsAudioDevice extends AudioDevice {
    public WindowsAudioDevice(ApplicationEventPublisher eventPublisher, String name, String id) {
        super(eventPublisher, name, id);
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
