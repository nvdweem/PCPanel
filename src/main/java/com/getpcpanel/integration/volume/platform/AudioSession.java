package com.getpcpanel.integration.volume.platform;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.enterprise.event.Event;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Exclude;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@SuppressWarnings("unused") // Methods called from JNI
public class AudioSession {
    public static final String SYSTEM = "System Sounds";
    @JsonIgnore @Exclude @ToString.Exclude @Nullable
    transient Event<Object> eventBus;
    private int pid;
    private File executable;
    @Exclude private String title;
    @Exclude @Nullable private String icon;
    @Exclude private float volume;
    @Exclude private boolean muted;

    public AudioSession(@Nullable Event<Object> eventBus, int pid, File executable, String title, @Nullable String icon, float volume, boolean muted) {
        this.eventBus = eventBus;
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

    /**
     * Returns {@code true} if {@code query} names this session. The comparison is case-insensitive and ignores a
     * trailing {@code .exe} on either side, so a Proton stream ({@code deadlock.exe}) and the window name a user
     * binds ({@code Deadlock}) resolve to the same stream (#96).
     * <p>
     * This is the single rule behind every "does this binding name this stream" decision - the dial that sets the
     * volume, the new-session restore that re-applies it, and the stored focus volume - so those can never disagree
     * about which streams an App-volume binding owns.
     */
    public boolean matches(@Nullable String query) {
        var normalized = stripExe(query);
        return StringUtils.isNotBlank(normalized) && matchKeys().stream().anyMatch(key -> StringUtils.equalsIgnoreCase(normalized, stripExe(key)));
    }

    /**
     * Every name this session can be addressed by. Subclasses add the identifiers their platform exposes.
     */
    @JsonIgnore
    protected Collection<String> matchKeys() {
        return Arrays.asList(executable == null ? null : executable.getName(), title);
    }

    private static String stripExe(@Nullable String value) {
        return StringUtils.removeEndIgnoreCase(StringUtils.trimToEmpty(value), ".exe");
    }

    protected AudioSession setVolumeNoTrigger(float volume) {
        this.volume = volume;
        return this;
    }

    private void triggerChange() {
        publish(new AudioSessionEvent(this, EventType.CHANGED));
    }

    /**
     * Hands an event to the bus. Overridden on Windows to get delivery off the audio backend's own
     * notification threads, which must not be held while arbitrary observers run.
     */
    protected void publish(Object event) {
        if (eventBus != null) {
            eventBus.fire(event);
        }
    }
}
