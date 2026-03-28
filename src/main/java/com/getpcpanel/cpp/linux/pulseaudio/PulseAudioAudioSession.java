package com.getpcpanel.cpp.linux.pulseaudio;

import java.io.File;

import com.getpcpanel.cpp.AudioSession;

import jakarta.enterprise.event.Event;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PulseAudioAudioSession extends AudioSession {
    private final int index;

    public PulseAudioAudioSession(Event<Object> eventPublisher, int index, int pid, File executable, String title, String icon, float volume, boolean muted) {
        super(eventPublisher, pid, executable, title, icon, volume, muted);
        this.index = index;
    }

    @Override
    protected AudioSession setVolumeNoTrigger(float volume) {
        return super.setVolumeNoTrigger(volume);
    }
}
