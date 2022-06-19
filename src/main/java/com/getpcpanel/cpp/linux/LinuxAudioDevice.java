package com.getpcpanel.cpp.linux;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;

import lombok.Getter;

@Getter
public class LinuxAudioDevice extends AudioDevice {
    private final int index;
    private final boolean isDefault;

    public LinuxAudioDevice(ApplicationEventPublisher eventPublisher, int index, String name, String id, boolean isDefault) {
        super(eventPublisher, name, id);
        this.index = index;
        this.isDefault = isDefault;
        dataflow(DataFlow.dfRender);
    }
}
