package com.getpcpanel.cpp.linux;

import java.io.File;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;

import lombok.Getter;

@Getter
public class LinuxAudioSession extends AudioSession {
    private final int index;

    public LinuxAudioSession(AudioDevice device, ApplicationEventPublisher eventPublisher, int index, int pid, File executable, String title, String icon, float volume, boolean muted) {
        super(device, eventPublisher, pid, executable, title, icon, volume, muted);
        this.index = index;
    }
}
