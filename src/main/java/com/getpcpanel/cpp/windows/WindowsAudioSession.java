package com.getpcpanel.cpp.windows;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.event.Event;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
public class WindowsAudioSession extends AudioSession {
    @ToString.Exclude private final AudioDevice device;
    private final Set<Long> pointers = new HashSet<>();

    public WindowsAudioSession(AudioDevice device, Event<Object> eventBus, int pid, File executable, String title, String icon,
            float volume, boolean muted) {
        super(eventBus, pid, executable, title, icon, volume, muted);
        this.device = device;
    }
}


import java.io.File;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.event.Event;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
public class WindowsAudioSession extends AudioSession {
    @ToString.Exclude private final AudioDevice device;
    private final Set<Long> pointers = new HashSet<>();

    public WindowsAudioSession(AudioDevice device, Event<Object> eventBus, int pid, File executable, String title, String icon,
            float volume, boolean muted) {
        super(eventPublisher, pid, executable, title, icon, volume, muted);
        this.device = device;
    }
}
