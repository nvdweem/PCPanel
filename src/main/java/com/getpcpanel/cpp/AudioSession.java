package com.getpcpanel.cpp;

import java.io.File;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@SuppressWarnings("unused") // Methods called from JNI
public class AudioSession {
    public static final String SYSTEM = "System Sounds";
    @EqualsAndHashCode.Exclude @ToString.Exclude @Nullable private final ApplicationEventPublisher eventPublisher;
    private int pid;
    private File executable;
    @EqualsAndHashCode.Exclude private String title;
    @EqualsAndHashCode.Exclude @Nullable private String icon;
    @EqualsAndHashCode.Exclude private float volume;
    @EqualsAndHashCode.Exclude private boolean muted;

    public AudioSession(@Nullable ApplicationEventPublisher eventPublisher, int pid, File executable, String title, @Nullable String icon, float volume, boolean muted) {
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
