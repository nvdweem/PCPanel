package com.getpcpanel.cpp;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;

import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@SuppressWarnings("unused") // Methods called from JNI
public class AudioSession {
    public static final String SYSTEM = "System Sounds";
    @ToString.Exclude private final ApplicationEventPublisher eventPublisher;
    private int pid;
    private File executable;
    private String title;
    private String icon;
    private float volume;
    private boolean muted;

    public AudioSession(ApplicationEventPublisher eventPublisher, int pid, File executable, String title, String icon, float volume, boolean muted) {
        this.eventPublisher = eventPublisher;
        this.pid = pid;
        this.executable = executable;
        this.icon = icon;
        this.volume = volume;
        this.muted = muted;

        // Uses pid and icon, so do this late
        this.title = isSystemSounds() ? SYSTEM : StringUtils.firstNonBlank(title, executable.getName());
    }

    public AudioSession name(String title) {
        this.title = title;
        triggerChange();
        return this;
    }

    private AudioSession title(String title) {
        this.title = title;
        triggerChange();
        return this;
    }

    private AudioSession icon(String icon) {
        this.icon = icon;
        triggerChange();
        return this;
    }

    private AudioSession volume(float volume) {
        this.volume = volume;
        triggerChange();
        return this;
    }

    private AudioSession muted(boolean muted) {
        this.muted = muted;
        triggerChange();
        return this;
    }

    public boolean isSystemSounds() {
        return pid == 0 || StringUtils.containsIgnoreCase(icon, "AudioSrv.Dll");
    }

    private void triggerChange() {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AudioSessionEvent(this, EventType.CHANGED));
        }
    }
}
