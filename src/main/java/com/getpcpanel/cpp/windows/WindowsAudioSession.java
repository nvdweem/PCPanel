package com.getpcpanel.cpp.windows;

import java.io.File;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;

public class WindowsAudioSession extends AudioSession {
    public WindowsAudioSession(AudioDevice device, ApplicationEventPublisher eventPublisher, int pid, File executable, String title, String icon,
            float volume, boolean muted) {
        super(device, eventPublisher, pid, executable, title, icon, volume, muted);
    }
}
