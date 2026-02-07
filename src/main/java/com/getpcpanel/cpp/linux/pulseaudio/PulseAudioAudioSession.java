package com.getpcpanel.cpp.linux.pulseaudio;

import java.io.File;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.cpp.AudioSession;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PulseAudioAudioSession extends AudioSession {
    private final int index;

    public PulseAudioAudioSession(ApplicationEventPublisher eventPublisher, int index, int pid, File executable, String title, String icon, float volume, boolean muted) {
        super(eventPublisher, pid, executable, title, icon, volume, muted);
        this.index = index;
    }
}
